package com.safemode.hbconnect.ui.common

/** A node in a flattened tree: the [item], its indentation [level], and whether it [hasChildren]. */
data class TreeRow<T>(val item: T, val id: String, val level: Int, val hasChildren: Boolean)

/**
 * Flattens a parent/child list into the rows currently visible given [isExpanded]. Items whose
 * parent id isn't present in the list are treated as roots, so nothing is hidden by a missing or
 * out-of-scope parent. Input order is preserved within each level.
 */
fun <T> flattenTree(
    items: List<T>,
    idOf: (T) -> String?,
    parentOf: (T) -> String?,
    isExpanded: (String) -> Boolean,
): List<TreeRow<T>> {
    val withId = items.mapNotNull { e -> idOf(e)?.let { it to e } }
    val ids = withId.mapTo(HashSet()) { it.first }
    val childrenByParent = LinkedHashMap<String?, MutableList<Pair<String, T>>>()
    withId.forEach { (id, e) ->
        val parent = parentOf(e)?.takeIf { it in ids }
        childrenByParent.getOrPut(parent) { mutableListOf() }.add(id to e)
    }
    val out = ArrayList<TreeRow<T>>()
    fun emit(parent: String?, level: Int) {
        childrenByParent[parent].orEmpty().forEach { (id, e) ->
            val hasChildren = !childrenByParent[id].isNullOrEmpty()
            out.add(TreeRow(e, id, level, hasChildren))
            if (hasChildren && isExpanded(id)) emit(id, level + 1)
        }
    }
    emit(null, 0)
    return out
}
