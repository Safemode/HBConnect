package com.safemode.hbconnect.data

import com.safemode.hbconnect.data.remote.models.EntityPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityHierarchyTest {
    private val house = EntityPath(id = "house", name = "House", type = "location")
    private val garage = EntityPath(id = "garage", name = "Garage", type = "location")
    private val toolbox = EntityPath(id = "toolbox", name = "Toolbox", type = "item")
    private val drawer = EntityPath(id = "drawer", name = "Drawer", type = "item")
    private val wrench = EntityPath(id = "wrench", name = "Wrench", type = "item")

    @Test
    fun nearestLocationSkipsParentItems() {
        assertEquals(garage, listOf(house, garage, toolbox, drawer, wrench).nearestLocation())
        assertEquals(garage, listOf(house, garage, toolbox).nearestLocation())
    }

    @Test
    fun nearestLocationIgnoresTheEntityItself() {
        assertEquals(house, listOf(house, garage).nearestLocation())
        assertNull(listOf(house).nearestLocation())
        assertNull(emptyList<EntityPath>().nearestLocation())
    }

    @Test
    fun knownIdsCountAsLocationsWhenTypeIsMissing() {
        val untyped = listOf(EntityPath(id = "garage"), EntityPath(id = "toolbox"), EntityPath(id = "wrench"))
        assertNull(untyped.nearestLocation())
        assertEquals("garage", untyped.nearestLocation(setOf("garage"))?.id)
    }

    @Test
    fun isWithinCatchesSelfAndDescendants() {
        val drawerPath = listOf(house, garage, toolbox, drawer)
        assertTrue(drawerPath.isWithin("toolbox"))
        assertTrue(drawerPath.isWithin("drawer"))
        assertFalse(drawerPath.isWithin("wrench"))
    }
}
