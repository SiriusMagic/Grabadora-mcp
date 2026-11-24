package com.zirakusa.grabadora.data

/** The flags put while recording (millis from the start), in order, never two within a second, at most [MAX]. */
object Markers {
    const val MAX = 99
    const val GAP = 1000L

    fun add(markers: List<Long>, at: Long): List<Long> {
        if (at < 0 || markers.size >= MAX || markers.any { kotlin.math.abs(it - at) < GAP }) return markers
        return (markers + at).sorted()
    }

    fun remove(markers: List<Long>, at: Long) = markers.filterNot { it == at }

    /** The first flag after [position] (a little ahead, so a tap while on a flag goes to the next one). */
    fun next(markers: List<Long>, position: Long) = markers.firstOrNull { it > position + 300 }
    fun previous(markers: List<Long>, position: Long) = markers.lastOrNull { it < position - 300 }

    /** Stored as «1200,5000»; broken parts are skipped. */
    fun parse(text: String?): List<Long> = text.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }
        .filter { it >= 0 }.distinct().sorted().take(MAX)
    fun serialize(markers: List<Long>) = markers.joinToString(",")
}

/** «Buscar»: every word must be in the name, without minding accents or letter case. Newest first. */
object Search {
    private val MARKS = Regex("\\p{Mn}+")
    fun fold(text: String): String = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replace(MARKS, "").lowercase()

    fun sorted(list: List<Recording>) = list.sortedWith(compareByDescending<Recording> { it.date }.thenBy { it.name })

    fun filter(list: List<Recording>, query: String): List<Recording> {
        val words = fold(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return list
        return list.filter { r -> val name = fold(r.name); words.all { it in name } }
    }
}

/** Selection mode (long press): which recordings are picked and what the bottom bar allows. */
data class Selection(val on: Boolean = false, val ids: Set<Long> = emptySet()) {
    val count get() = ids.size
    val canShare get() = ids.isNotEmpty()
    val canDelete get() = ids.isNotEmpty()
    /** Renombrar and Detalles are for one recording at a time. */
    val canRename get() = ids.size == 1
    val canDetails get() = ids.size == 1

    fun start(id: Long) = Selection(true, setOf(id))
    fun toggle(id: Long) = copy(ids = if (id in ids) ids - id else ids + id)
    fun allOf(list: List<Recording>) = list.isNotEmpty() && list.all { it.id in ids }
    /** «Todo»: picks every recording shown, or none when all are picked already. */
    fun toggleAll(list: List<Recording>) = copy(ids = if (allOf(list)) emptySet() else list.map { it.id }.toSet())
    /** Drops what is no longer there (deleted, or hidden by the search). */
    fun keep(list: List<Recording>) = copy(ids = ids.intersect(list.map { it.id }.toSet()))
    fun picked(list: List<Recording>) = list.filter { it.id in ids }

    companion object { val NONE = Selection() }
}
