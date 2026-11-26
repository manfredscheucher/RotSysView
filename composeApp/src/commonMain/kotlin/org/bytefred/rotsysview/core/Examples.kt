package org.bytefred.rotsysview.core

/** A link of a supergroup, a group or a set: a paper ("Paper: ...") or a website. */
data class ExampleLink(val title: String, val url: String)

/**
 * A top-level node of the examples tree (`composeApp/examples/index.json`, `"supergroups"`), e.g.
 * Database or SAT framework examples. [id] is unique among the supergroups.
 */
class ExampleSupergroup(
    val id: String,
    val title: String,
    /** One or two short sentences, or null. */
    val description: String?,
    /** Shown below its header. */
    val links: List<ExampleLink>,
) {
    override fun toString() = id
}

/**
 * A group of bundled examples inside its [supergroup] (in display order, as in the index). [id] is
 * unique among all groups (the loader checks it, see [Examples.group]).
 */
class ExampleGroup(
    val supergroup: ExampleSupergroup,
    val id: String,
    val title: String,
    /** One or two short sentences, or null. */
    val description: String?,
    /** Its links (none in RotSysView: the sources are on the sets); left out when they are exactly the supergroup's. */
    val links: List<ExampleLink>,
) {
    override fun toString() = "${supergroup.id}/$id"
}

/** A family generated for any n in [minN]..[maxN] (rotsys-sat's formulas), the first group of supergroup [supergroupId]. */
enum class ExampleFamily(val title: String, val description: String, val minN: Int, val maxN: Int, val generate: (Int) -> RotationSystem) {
    PERFECT_CONVEX(
        "Perfect convex C_n", "Vertex k sees the other vertices in increasing order.", 4, 20, RotationSystem::perfectConvex,
    ),
    PERFECT_TWISTED(
        "Perfect twisted T_n", "Vertex k sees 0..k-1 increasing, then n-1..k+1 decreasing.", 4, 18, RotationSystem::perfectTwisted,
    ),
    ;

    val link: ExampleLink get() = ExampleLink("Paper: Bergold, Scheucher: Investigating Simple Drawings of K_n using SAT", "https://arxiv.org/abs/2504.02650")
    val supergroupId: String get() = "sat-framework"
    val id: String get() = name.lowercase().replace('_', '-')
}

/**
 * A bundled file of rotation systems (often just one), PoCoView's example format: a JSON object with
 * the metadata and `"rotation_systems"`, the list of rotation systems `[[1,2,3],[0,2,3],...]`
 * (0-based, one per line). The same object is RotSysView's saved JSON file, so Open reads it too.
 * Members are decoded on demand, so the 11556 drawings of K_7 cost nothing until one is shown. A
 * member of a list is named `<name> #<index + 1>`; the dialog shows the [label].
 */
class ExampleSet internal constructor(
    /** The file below `composeApp/examples/`. */
    val path: String,
    val name: String,
    val group: ExampleGroup,
    val n: Int,
    /** Short, understandable without the description (the tree shows it). */
    val label: String,
    /** One short sentence (may be empty: the group's says it all). */
    val description: String,
    /** Its sources (papers, OEIS, data), shown below its description and parameters. */
    val links: List<ExampleLink>,
    /** Where the data comes from, null if typed in. */
    val source: String?,
    private val members: List<Any?>,
) {
    private val decoded = HashMap<Int, RotationSystem>()

    /** Number of members. */
    val size: Int get() = members.size

    /** Member [index], decoded. */
    fun rotationSystem(index: Int): RotationSystem = decoded.getOrPut(index) {
        try {
            rotationSystemOfJson(members[index])
        } catch (e: InvalidRotationSystemException) {
            error("$path, rotation system ${index + 1}: ${e.message}")
        }
    }

    /** The stored text of member [index] (its JSON line). */
    fun text(index: Int): String = rotationSystem(index).toJson()

    fun memberName(index: Int): String = if (size == 1) name else "$name #${index + 1}"

    fun position(index: Int): String = "#${index + 1} of $size"
}

/** A member of an [ExampleSet]. */
data class ExampleRef(val set: ExampleSet, val index: Int) {
    val name: String get() = set.memberName(index)

    fun rotationSystem(): RotationSystem = set.rotationSystem(index)
}

/**
 * The catalogue of bundled examples (`composeApp/examples/index.json`, ArrView's and PoCoView's
 * schema): supergroups, their groups, the sets of each group, and the generated [ExampleFamily]s.
 * Data provenance and the schema: `doc/DEVELOPMENT.md`.
 */
object Examples {
    /** The supergroups in display order. */
    val supergroups: List<ExampleSupergroup>

    /** All groups, supergroup after supergroup, in display order. */
    val groups: List<ExampleGroup>

    /** Every listing of a file: the sets of every group, in the order of the index. */
    private val listings: List<ExampleSet>

    /** All sets, one per file (the first listing of a file listed twice), in the order of the index. */
    val sets: List<ExampleSet>

    init {
        val index = jsonObject(parseJson(bundled(INDEX)), INDEX)
        val supergroups = ArrayList<ExampleSupergroup>()
        val groups = ArrayList<ExampleGroup>()
        val files = ArrayList<Pair<ExampleGroup, String>>()
        for (s in index.list("supergroups", INDEX)) {
            val so = jsonObject(s, INDEX)
            // the sources are on the sets (Manfred), as in ArrView and PoCoView
            require("links" !in so) { "$INDEX: supergroup '${so["id"]}' has links; they belong to its sets" }
            val sg = ExampleSupergroup(so.string("id", INDEX), so.string("title", INDEX), so.optString("description", INDEX), links(so, INDEX))
            require(supergroups.none { it.id == sg.id }) { "$INDEX: supergroup id '${sg.id}' twice" }
            supergroups += sg
            for (g in so.list("groups", INDEX)) {
                val go = jsonObject(g, INDEX)
                require("links" !in go) { "$INDEX: group '${go["id"]}' has links; they belong to its sets" }
                val group = ExampleGroup(sg, go.string("id", INDEX), go.string("title", INDEX), go.optString("description", INDEX), links(go, INDEX))
                groups += group
                val paths = go.list("files", INDEX).map { it as? String ?: error("$INDEX: file names must be strings") }
                require(paths.size == paths.toSet().size) { "$INDEX: a file twice in '$group'" }
                for (p in paths) files += group to p
            }
        }
        checkGroupIds(groups)
        // a file listed twice is parsed once; its "group" names one of the groups or supergroups listing it
        val parsed = HashMap<String, Map<*, *>>()
        val listings = files.map { (group, path) ->
            val o = parsed.getOrPut(path) { jsonObject(parseJson(bundled(path)), path) }
            val titles = files.filter { it.second == path }.flatMap { listOf(it.first.title, it.first.supergroup.title) }
            require(o["group"] in titles) { "$path: \"group\" is none of $titles" }
            parseSet(path, o, group)
        }
        this.supergroups = supergroups
        this.groups = groups
        this.listings = listings
        this.sets = listings.distinctBy { it.path }
    }

    /** The index file: the supergroups with their groups and files. */
    const val INDEX = "index.json"

    /** One listing of an example file ([o], parsed) in [group]: the metadata and the members (decoded on demand). */
    private fun parseSet(path: String, o: Map<*, *>, group: ExampleGroup): ExampleSet {
        val n = (o["n"] as? Long)?.toInt() ?: error("$path: \"n\" must be a number")
        val members = o.list("rotation_systems", path).takeIf { it.isNotEmpty() } ?: error("$path: \"rotation_systems\" is empty")
        return ExampleSet(
            path, o.string("name", path), group, n, o.string("label", path), o.string("description", path),
            links(o, path), o.optString("source", path), members,
        )
    }

    private fun bundled(path: String): String =
        GeneratedExamples.FILES[path]?.joinToString("") ?: error("example file $path is not bundled")

    private fun jsonObject(v: Any?, file: String): Map<*, *> = v as? Map<*, *> ?: error("$file: expected a JSON object")

    private fun Map<*, *>.string(key: String, file: String): String = this[key] as? String ?: error("$file: \"$key\" must be a string")

    private fun Map<*, *>.optString(key: String, file: String): String? =
        if (this[key] == null) null else this[key] as? String ?: error("$file: \"$key\" must be a string")

    private fun Map<*, *>.list(key: String, file: String): List<*> = this[key] as? List<*> ?: error("$file: \"$key\" must be a list")

    /** The optional "links" of [o]. */
    private fun links(o: Map<*, *>, file: String): List<ExampleLink> = (o["links"]?.let { o.list("links", file) } ?: emptyList<Any?>()).map {
        val l = jsonObject(it, file)
        ExampleLink(l.string("title", file), l.string("url", file))
    }

    /** The supergroup with the given [id]. */
    fun supergroup(id: String): ExampleSupergroup = supergroups.first { it.id == id }

    /** Group ids are unique among all groups, not only within a supergroup ([group] looks a group up by its id alone). */
    internal fun checkGroupIds(groups: List<ExampleGroup>) {
        val twice = groups.groupBy { it.id }.values.firstOrNull { it.size > 1 } ?: return
        error("$INDEX: group id '${twice[0].id}' twice (in ${twice.joinToString { "'${it.supergroup.id}'" }})")
    }

    /** The group with the given [id] (group ids are unique among all groups, see [checkGroupIds]). */
    fun group(id: String): ExampleGroup = groups.single { it.id == id }

    /** The groups of [supergroup], in display order. */
    fun groupsIn(supergroup: ExampleSupergroup): List<ExampleGroup> = groups.filter { it.supergroup == supergroup }

    /** The families of [supergroup] (shown as its first group). */
    fun familiesIn(supergroup: ExampleSupergroup): List<ExampleFamily> = ExampleFamily.entries.filter { it.supergroupId == supergroup.id }

    /** The set with the given [name] (its first listing), or null. */
    fun byName(name: String): ExampleSet? = sets.firstOrNull { it.name == name }

    /** The sets of [group], in the order of the index. */
    fun inGroup(group: ExampleGroup): List<ExampleSet> = listings.filter { it.group == group }

    /** The example shown at start: the first drawing of K_6. */
    val start: ExampleRef get() = ExampleRef(byName("n6-all")!!, 0)
}
