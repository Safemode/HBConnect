package com.safemode.hbconnect.data

import com.safemode.hbconnect.data.queue.UploadQueueManager
import com.safemode.hbconnect.data.remote.HttpStatusException
import com.safemode.hbconnect.data.remote.ServerUnreachableException
import com.safemode.hbconnect.data.remote.isUnreachable
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.data.remote.models.EntityTypeSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class UnreachableTest {
    @Test
    fun networkFailuresAndGatewayErrorsAreUnreachable() {
        assertTrue(ServerUnreachableException().isUnreachable())
        assertTrue(SocketTimeoutException("read timed out").isUnreachable())
        assertTrue(HttpStatusException(502, "Bad gateway").isUnreachable())
        assertTrue(HttpStatusException(503, "Unavailable").isUnreachable())
    }

    @Test
    fun answersFromHomeboxAreNotUnreachable() {
        assertFalse(HttpStatusException(401, "Unauthorized").isUnreachable())
        assertFalse(HttpStatusException(404, "Not found").isUnreachable())
        assertFalse(HttpStatusException(500, "Server error").isUnreachable())
        assertFalse(IllegalStateException("Connection not configured.").isUnreachable())
    }
}

class LocationViaTest {
    private val locationType = EntityTypeSummary(id = "lt", isLocation = true)
    private val itemType = EntityTypeSummary(id = "it", isLocation = false)
    private val garage = EntitySummary(id = "garage", name = "Garage", entityType = locationType)

    @Test
    fun followsParentItemsUpToTheirLocation() {
        val toolbox = EntitySummary(id = "toolbox", name = "Toolbox", entityType = itemType, parent = garage)
        val bit = EntitySummary(id = "bit", name = "Drill bit", entityType = itemType, parent = toolbox.copy(parent = null))
        val byId = listOf(toolbox, bit).associateBy { it.id }
        assertEquals("garage", bit.locationVia(byId)?.id)
        assertEquals("garage", toolbox.locationVia(byId)?.id)
    }

    @Test
    fun aParentMissingFromTheListIsTakenAsTheLocationUnlessKnownToBeAnItem() {
        val shelf = EntitySummary(id = "shelf", name = "Shelf")
        assertEquals("shelf", EntitySummary(id = "a", parent = shelf).locationVia(emptyMap())?.id)
        val archivedItem = EntitySummary(id = "box", entityType = itemType)
        assertNull(EntitySummary(id = "b", parent = archivedItem).locationVia(emptyMap()))
    }

    @Test
    fun aCycleEndsWithoutALocation() {
        val a = EntitySummary(id = "a", entityType = itemType, parent = EntitySummary(id = "b", entityType = itemType))
        val b = EntitySummary(id = "b", entityType = itemType, parent = EntitySummary(id = "a", entityType = itemType))
        assertNull(a.locationVia(listOf(a, b).associateBy { it.id }))
    }
}

class PhotoAndQueueRulesTest {
    @Test
    fun sampleSizeKeepsTheLongEdgeAtOrAboveTheTarget() {
        assertEquals(1, photoSampleSize(2000, 1500))
        assertEquals(2, photoSampleSize(4096, 3072))
        assertEquals(1, photoSampleSize(4032, 3024))
        assertEquals(4, photoSampleSize(8192, 6144))
    }

    @Test
    fun failedOpsStopRetryingOnTheirOwnButNotForTheUser() {
        assertTrue(UploadQueueManager.shouldAttempt(0, manual = false))
        assertTrue(UploadQueueManager.shouldAttempt(UploadQueueManager.MAX_AUTO_ATTEMPTS - 1, manual = false))
        assertFalse(UploadQueueManager.shouldAttempt(UploadQueueManager.MAX_AUTO_ATTEMPTS, manual = false))
        assertTrue(UploadQueueManager.shouldAttempt(UploadQueueManager.MAX_AUTO_ATTEMPTS, manual = true))
    }
}
