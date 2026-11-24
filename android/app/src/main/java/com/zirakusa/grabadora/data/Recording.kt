package com.zirakusa.grabadora.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * One recording as the app shows it. [id] is MediaStore's id (or, on Android 8–9, one made from the file name);
 * [date] in epoch millis; [markers] the flags put while recording (millis from the start); [own] is false for
 * recordings this install did not make (before reinstalling), which Android asks the user about before changing.
 */
data class Recording(val id: Long, val name: String, val fileName: String, val date: Long, val durationMs: Long,
    val size: Long, val location: String = Names.FOLDER, val markers: List<Long> = emptyList(), val uri: String = "",
    val own: Boolean = true)

/** File names: «20261121_063640» by default, safe characters only, always .m4a, never two alike. */
object Names {
    const val FOLDER = "Music/Grabadora"
    const val EXTENSION = ".m4a"
    const val MAX = 80
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private val BAD = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    fun default(at: LocalDateTime): String = STAMP.format(at)

    /** A name that is safe as a file: no folder or reserved characters, no leading dot, no .m4a; null when empty. */
    fun clean(text: String): String? {
        var t = text.replace(BAD, "").trim()
        if (t.endsWith(EXTENSION, ignoreCase = true)) t = t.dropLast(EXTENSION.length).trim()
        t = t.trimStart('.', ' ').take(MAX).trim()
        return t.ifEmpty { null }
    }

    fun file(name: String) = name + EXTENSION

    /** «Reunión.m4a» → «Reunión». */
    fun of(fileName: String) = if ('.' in fileName.drop(1)) fileName.substringBeforeLast('.') else fileName

    /** [name], or «name (2)», «name (3)»… when another recording already has it (letter case does not count). */
    fun unique(name: String, taken: Collection<String>): String {
        val lower = taken.map { it.lowercase() }.toSet()
        if (name.lowercase() !in lower) return name
        return generateSequence(2) { it + 1 }.map { "$name ($it)" }.first { it.lowercase() !in lower }
    }
}
