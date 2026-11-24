package org.bytefred.rotsysview.core

/** File extensions offered when opening a file. */
val LOAD_EXTENSIONS = listOf("json", "json0", "txt")

/**
 * The editable text forms of a rotation system (Encoding widget, Import / Export); [noun] is the
 * form in messages ("Applied the rotations").
 */
enum class TextFormat(val label: String, val noun: String) {
    /** pyrslib's `to_string`: one rotation per line, 0-based. */
    ROWS("Rotations (one line per vertex)", "rotations"),

    /** pyrslib's `to_json_string` / rotsys-sat's output: `[[1,2,3],[0,2,3],...]`. */
    JSON("JSON", "JSON"),
    ;

    fun write(rs: RotationSystem): String = when (this) {
        ROWS -> rs.toText()
        JSON -> rs.toJson()
    }

    /**
     * Reads one rotation system in any of the formats ([readRotationSystems] with exactly one).
     * @throws InvalidRotationSystemException with the reason
     */
    fun read(text: String): RotationSystem {
        val all = readRotationSystems(text)
        if (all.size != 1) throw InvalidRotationSystemException("expected one rotation system, found ${all.size}")
        return all.single().rs
    }
}

/** The formats of a saved file. */
enum class FileFormat(val extension: String) { JSON("json"), TXT("txt") }

/**
 * File content: JSON `{"name": ..., "rotation_systems": [[[1,2,3],...], ...]}`, or text with one
 * JSON line per rotation system (as rotsys-sat prints them).
 */
fun rotationSystemsFile(name: String, all: List<RotationSystem>, format: FileFormat): String = when (format) {
    FileFormat.JSON -> "{\n  \"name\": ${jsonString(name)},\n  \"rotation_systems\": [\n" +
        all.joinToString(",\n") { "    " + it.toJson() } + "\n  ]\n}\n"
    FileFormat.TXT -> all.joinToString("\n", postfix = "\n") { it.toJson() }
}

/** A rotation system read from text, with the name a JSON file gives it. */
class NamedRotationSystem(val rs: RotationSystem, val name: String?)

/**
 * Reads all rotation systems of a text. Accepted, detected by their look:
 * - a JSON object `{"name": ..., "rotation_systems": [...]}` (as saved),
 * - JSON lines `[[1,2,3],[0,2,3],...]`, one system per line (rotsys-sat, pyrslib `to_json_string`),
 * - lines `n 0  1:432 2:431 ...` (1-based, the data of Balogh, Lidický, Salazar; lines for n < 3 are skipped),
 * - rotation rows (pyrslib `to_string`): one line per vertex, systems separated by an empty line,
 *   optionally each preceded by a line with n alone (pyrslib's text files). Rows are 0-based unless
 *   they use the numbers 1..n.
 * @throws InvalidRotationSystemException with the reason (and the number of the failing system)
 */
fun readRotationSystems(text: String): List<NamedRotationSystem> {
    val trimmed = text.trim().removePrefix("﻿")
    if (trimmed.isEmpty()) throw InvalidRotationSystemException("the text is empty")
    if (trimmed.startsWith("{")) return readJsonFile(trimmed)
    val lines = trimmed.lines().map { it.trim() }
    if (trimmed.startsWith("[")) {
        val nonEmpty = lines.filter { it.isNotEmpty() }
        // one system may also be spread over several lines
        val items = if (nonEmpty.all { it.startsWith("[[") }) nonEmpty else listOf(trimmed)
        return items.mapIndexed { i, line -> numbered(i, items.size) { NamedRotationSystem(fromJson(parseJson(line)), null) } }
    }
    if (Regex("""^\d+\s+\d+\s+(\d+:\d*\s*)*$""").matches(lines.first())) {
        val items = lines.filter { it.isNotEmpty() }.mapNotNull { hillLine(it) }
        if (items.isEmpty()) throw InvalidRotationSystemException("no rotation system with n >= 3")
        return items.mapIndexed { i, line -> numbered(i, items.size) { NamedRotationSystem(parseHill(line), null) } }
    }
    val blocks = rowBlocks(lines)
    return blocks.mapIndexed { i, b -> numbered(i, blocks.size) { NamedRotationSystem(parseRows(b), null) } }
}

/** [read] with "rotation system i of m: " before its error. */
private fun numbered(i: Int, m: Int, read: () -> NamedRotationSystem): NamedRotationSystem =
    try {
        read()
    } catch (e: InvalidRotationSystemException) {
        throw InvalidRotationSystemException(if (m == 1) e.message ?: "" else "rotation system ${i + 1} of $m: ${e.message}")
    }

private fun readJsonFile(text: String): List<NamedRotationSystem> {
    val root = parseJson(text) as? Map<*, *> ?: throw InvalidRotationSystemException("JSON: expected an object")
    val name = root["name"]?.let { it as? String ?: throw InvalidRotationSystemException("JSON: \"name\" must be a string") }
    val list = root["rotation_systems"] as? List<*>
        ?: throw InvalidRotationSystemException("JSON: expected \"rotation_systems\": [[[...], ...], ...]")
    if (list.isEmpty()) throw InvalidRotationSystemException("JSON: the list is empty")
    return list.mapIndexed { i, item -> numbered(i, list.size) { NamedRotationSystem(fromJson(item), if (list.size == 1) name else name?.let { "$it #${i + 1}" }) } }
}

/** A parsed JSON value `[[1,2,3],[0,2,3],...]` (0-based), e.g. a member of an example file. */
internal fun rotationSystemOfJson(value: Any?): RotationSystem = fromJson(value)

/** A JSON value `[[1,2,3],[0,2,3],...]` (0-based). */
private fun fromJson(value: Any?): RotationSystem {
    val rows = value as? List<*> ?: throw InvalidRotationSystemException("JSON: expected a list of rotations")
    return RotationSystem(
        rows.mapIndexed { a, row ->
            (row as? List<*>)?.map { e ->
                (e as? Long)?.takeIf { it in 0..1000 }?.toInt() ?: throw InvalidRotationSystemException("JSON: vertex $a: $e is not a vertex")
            } ?: throw InvalidRotationSystemException("JSON: the rotation of vertex $a is not a list")
        },
    )
}

/** A Lidický line, or null for n < 3. */
private fun hillLine(line: String): String? = line.takeIf { (it.split(Regex("\\s+")).first().toIntOrNull() ?: 0) >= 3 }

/** `n 0  1:432 2:431 ...`: 1-based, one digit per vertex. */
private fun parseHill(line: String): RotationSystem {
    val parts = line.split(Regex("\\s+"))
    val n = parts[0].toInt()
    val rows = parts.drop(2).filter { it.contains(':') }.map { part ->
        part.substringAfter(':').map { ch -> ch.digitToIntOrNull()?.minus(1) ?: throw InvalidRotationSystemException("'$part' is not a rotation") }
    }
    if (rows.size != n) throw InvalidRotationSystemException("expected $n rotations, found ${rows.size}")
    return RotationSystem(rows)
}

/** The blocks of rotation rows: separated by empty lines or by a line holding n alone. */
private fun rowBlocks(lines: List<String>): List<List<String>> {
    val blocks = ArrayList<List<String>>()
    var current = ArrayList<String>()
    var expected = -1
    fun flush() {
        if (current.isNotEmpty()) blocks += current
        current = ArrayList()
    }
    for (line in lines) {
        val numbers = line.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        when {
            line.isEmpty() -> flush()
            numbers.size == 1 && current.isEmpty() -> expected = numbers[0].toIntOrNull() ?: -1
            else -> {
                current += line
                if (expected > 0 && current.size == expected) flush()
            }
        }
    }
    flush()
    if (blocks.isEmpty()) throw InvalidRotationSystemException("no rotation rows found")
    return blocks
}

/** One rotation per line; 1-based if the numbers are 1..n. */
private fun parseRows(lines: List<String>): RotationSystem {
    val rows = lines.map { line ->
        line.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.map {
            it.toIntOrNull() ?: throw InvalidRotationSystemException("'$it' is not a number")
        }
    }
    val n = rows.size
    val all = rows.flatten()
    val oneBased = all.isNotEmpty() && all.min() >= 1 && all.max() == n
    return RotationSystem(if (oneBased) rows.map { r -> r.map { it - 1 } } else rows)
}
