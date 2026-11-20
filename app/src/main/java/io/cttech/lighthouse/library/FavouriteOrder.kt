package io.cttech.lighthouse.library

/** Reordering favourites as HOME's Favourites row shows them. */
object FavouriteOrder {
    /**
     * [all] (every favourite, in order) with [key] moved [delta] places among [shown], the ones HOME shows. Favourites
     * HOME leaves out (hidden, gone, past the row's end) keep their places, so every press visibly moves the card.
     * Null when it can't move (already at that end, or not shown).
     */
    fun move(all: List<String>, shown: List<String>, key: String, delta: Int): List<String>? {
        val visible = shown.filter { it in all }
        val from = visible.indexOf(key).takeIf { it >= 0 } ?: return null
        val to = (from + delta).coerceIn(0, visible.lastIndex)
        if (to == from) return null
        val reordered = visible.toMutableList().apply { add(to, removeAt(from)) }.iterator()
        val visibleSet = visible.toSet()
        return all.map { if (it in visibleSet) reordered.next() else it }
    }
}
