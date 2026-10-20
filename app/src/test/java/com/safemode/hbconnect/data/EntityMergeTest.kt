package com.safemode.hbconnect.data

import com.safemode.hbconnect.data.remote.models.EntityUpdate
import org.junit.Assert.assertEquals
import org.junit.Test

class EntityMergeTest {
    private val base = EntityUpdate(
        id = "e1",
        name = "Drill",
        notes = "old notes",
        purchasePrice = 50.0,
        archived = false,
        tagIds = listOf("t1", "t2"),
    )

    @Test
    fun keepsServerChangesToFieldsTheUserDidNotTouch() {
        val edited = base.copy(name = "Cordless drill")
        val current = base.copy(notes = "changed elsewhere", purchasePrice = 75.0)

        val merged = mergeEdit(base, edited, current)

        assertEquals("Cordless drill", merged.name)
        assertEquals("changed elsewhere", merged.notes)
        assertEquals(75.0, merged.purchasePrice!!, 0.0)
    }

    @Test
    fun ownLocationOfASubItemIsMergedAndKept() {
        val sub = base.copy(parentId = "toolbox", locationId = "garage")
        // Untouched: the server's current own location survives an unrelated edit.
        assertEquals("garage", mergeEdit(sub, sub.copy(name = "Drill bit"), sub).locationId)
        // Edited: clearing it (back to the parent item's location) is applied.
        assertEquals(null, mergeEdit(sub, sub.copy(locationId = null), sub).locationId)
    }

    @Test
    fun userEditWinsOnAFieldBothChanged() {
        val edited = base.copy(notes = "mine")
        val current = base.copy(notes = "theirs")

        assertEquals("mine", mergeEdit(base, edited, current).notes)
    }

    @Test
    fun clearingAFieldIsKeptAsAnEdit() {
        val edited = base.copy(notes = null)
        val current = base.copy(notes = "theirs")

        assertEquals(null, mergeEdit(base, edited, current).notes)
    }

    @Test
    fun tagsMergePerTag() {
        // User removed t1 and added t3; meanwhile t4 was added on the server.
        val edited = base.copy(tagIds = listOf("t2", "t3"))
        val current = base.copy(tagIds = listOf("t1", "t2", "t4"))

        assertEquals(setOf("t2", "t3", "t4"), mergeEdit(base, edited, current).tagIds!!.toSet())
    }

    @Test
    fun untouchedTagsFollowTheServer() {
        val edited = base.copy(name = "Renamed")
        val current = base.copy(tagIds = listOf("t9"))

        assertEquals(listOf("t9"), mergeEdit(base, edited, current).tagIds)
    }
}
