package org.bytefred.rotsysview.core

import java.io.File
import kotlin.test.Test

/**
 * One-off (as ArrView's scripts/probe/ConvertExamplesProbe.kt): writes the catalogue of the former
 * Examples.kt as PoCoView's example format, `build/examples-json/index.json` plus one JSON file per set.
 */
class ConvertExamplesProbe {
    private fun q(s: String) = jsonString(s)

    private fun link(p: Paper) = "{\"title\": ${q("Paper: " + p.title)}, \"url\": ${q(p.url)}}"

    private val dirs = mapOf(
        ExampleGroup.ALL to "all", ExampleGroup.CONVEXITY to "convexity", ExampleGroup.ALL_EDGES_CROSSED to "all-edges-crossed",
        ExampleGroup.CROSSING_FAMILIES to "crossing-families", ExampleGroup.AVOIDING to "avoiding",
        ExampleGroup.EMPTY_TRIANGLES to "empty-triangles", ExampleGroup.PLANE_HC to "plane-hc",
    )
    private val sgIds = mapOf(ExampleSupergroup.DATABASE to "database", ExampleSupergroup.SAT_FRAMEWORK to "sat-framework")

    @Test
    fun convert() {
        val out = File("build/examples-json").apply { deleteRecursively(); mkdirs() }
        val index = StringBuilder("{\n  \"supergroups\": [\n")
        index.append(
            ExampleSupergroup.entries.joinToString(",\n") { sg ->
                val links = sg.papers.joinToString(",\n        ", "[\n        ", "\n      ]") { link(it) }
                val groups = Examples.groupsIn(sg).joinToString(",\n") { g ->
                    val files = Examples.inGroup(g).map { "${dirs.getValue(g)}/${it.name}.json" }
                    for (set in Examples.inGroup(g)) {
                        val members = (0 until set.size).joinToString(",\n    ", "[\n    ", "\n  ]") { set.rotationSystem(it).toJson() }
                        val text = "{\n  \"name\": ${q(set.name)},\n  \"n\": ${set.n},\n  \"group\": ${q(g.title)},\n" +
                            "  \"label\": ${q(set.name)},\n  \"description\": ${q(set.description)},\n" +
                            "  \"source\": ${q(set.source ?: "")},\n  \"rotation_systems\": $members\n}\n"
                        File(out, files[Examples.inGroup(g).indexOf(set)]).apply { parentFile.mkdirs() }.writeText(text)
                    }
                    val glinks = g.paper?.let { ",\n          \"links\": [${link(it)}]" } ?: ""
                    "        {\n          \"id\": ${q(dirs.getValue(g))},\n          \"title\": ${q(g.title)},\n" +
                        "          \"description\": ${q(g.description)}$glinks,\n          \"files\": " +
                        files.joinToString(",\n            ", "[\n            ", "\n          ]") { q(it) } + "\n        }"
                }
                "    {\n      \"id\": ${q(sgIds.getValue(sg))},\n      \"title\": ${q(sg.title)},\n      \"description\": ${q(sg.description)},\n" +
                    (if (sg.papers.isEmpty()) "" else "      \"links\": $links,\n") +
                    "      \"groups\": [\n$groups\n      ]\n    }"
            },
        )
        index.append("\n  ]\n}\n")
        File(out, "index.json").writeText(index.toString())
    }
}
