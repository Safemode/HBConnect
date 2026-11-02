package com.safemode.hbconnect.data.queue

import com.safemode.hbconnect.data.HomeboxRepository
import com.safemode.hbconnect.data.local.QueuedOpDao
import com.safemode.hbconnect.data.local.QueuedOpEntity
import com.safemode.hbconnect.data.remote.ApiProvider
import com.safemode.hbconnect.data.remote.ServerStatus
import com.safemode.hbconnect.data.remote.models.EntityPatch
import com.safemode.hbconnect.data.settings.ConnectionConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** An in-memory stand-in for the Room outbox table, in the same FIFO order. */
private class FakeQueuedOpDao : QueuedOpDao {
    val rows = MutableStateFlow<List<QueuedOpEntity>>(emptyList())
    private fun sorted(list: List<QueuedOpEntity>) = list.sortedWith(compareBy({ it.createdAt }, { it.id }))
    override fun observeAll(): Flow<List<QueuedOpEntity>> = rows.map(::sorted)
    override suspend fun getAll(): List<QueuedOpEntity> = sorted(rows.value)
    override fun observeCount(): Flow<Int> = rows.map { it.size }
    override suspend fun upsert(op: QueuedOpEntity) {
        rows.value = rows.value.filterNot { it.id == op.id } + op
    }
    override suspend fun upsertAll(ops: List<QueuedOpEntity>) = ops.forEach { upsert(it) }
    override suspend fun forServer(serverId: String): List<QueuedOpEntity> = rows.value.filter { it.serverId == serverId }
    override suspend fun delete(id: String) {
        rows.value = rows.value.filterNot { it.id == id }
    }
    override suspend fun clear() {
        rows.value = emptyList()
    }
}

/** Drives [UploadQueueManager] against a local HTTP server to check how the outbox replays. */
class UploadQueueManagerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val server = MockWebServer()
    private val dao = FakeQueuedOpDao()
    private val drainResults = mutableListOf<Pair<Int, Int>>()
    private var target = QueueTarget(server = "s1", tenant = "", account = "u1")
    private lateinit var queue: UploadQueueManager

    @Before
    fun setUp() {
        server.start()
        val status = ServerStatus().apply { report(true) }
        val api = ApiProvider(temp.newFolder("cache"), status) { true }
        api.updateConfig(
            ConnectionConfig(serverId = "s1", scheme = "http", host = server.hostName, port = server.port.toString(), token = "t"),
        )
        queue = UploadQueueManager(
            dao = dao,
            repository = HomeboxRepository(api),
            queueDir = temp.newFolder("queue"),
            serverStatus = status,
            isOnline = { true },
            activeTarget = { target },
            notificationsEnabled = { true },
            onDrainComplete = { uploaded, failed -> drainResults += uploaded to failed },
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun ok() = MockResponse.Builder().code(200).body("{}").build()
    private fun error() = MockResponse.Builder().code(500).body("boom").build()

    private suspend fun queuePatch(entityId: String) =
        queue.enqueue(OpKind.PATCH_ENTITY, EntityPatch(id = entityId, quantity = 2.0), targetId = entityId, label = "Edit $entityId")

    private fun sentPaths(): List<String> = List(server.requestCount) { server.takeRequest().url.encodedPath }

    @Test
    fun aFailedWriteHoldsBackLaterWritesToTheSameEntity() = runBlocking {
        queuePatch("e1")
        queuePatch("e1")
        queuePatch("e2")
        server.enqueue(error())
        server.enqueue(ok())

        queue.drain()

        // The second e1 edit waits behind the failed one; e2 is independent and goes through.
        assertEquals(listOf("/api/v1/entities/e1", "/api/v1/entities/e2"), sentPaths())
        val left = dao.getAll()
        assertEquals(2, left.size)
        assertTrue(left.all { it.targetId == "e1" })
        assertEquals(1, left.first().attempts)
        assertEquals(0, left.last().attempts)
    }

    @Test
    fun anOpThatKeepsFailingStopsRetryingUntilTheUserAsks() = runBlocking {
        queuePatch("e1")
        dao.upsert(dao.getAll().single().copy(attempts = UploadQueueManager.MAX_AUTO_ATTEMPTS))

        queue.drain()
        assertEquals(0, server.requestCount)

        server.enqueue(ok())
        queue.drain(manual = true)
        assertEquals(1, server.requestCount)
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun writesQueuedForAnotherAccountWait() = runBlocking {
        target = target.copy(account = "u2")
        queuePatch("e1")
        target = target.copy(account = "u1")

        queue.drain()

        assertEquals(0, server.requestCount)
        assertEquals("u2", dao.getAll().single().accountId)
    }

    @Test
    fun aSwitchMidDrainStopsThePass() = runBlocking {
        queuePatch("e1")
        queuePatch("e2")
        server.enqueue(ok())
        var calls = 0
        val switching = UploadQueueManager(
            dao = dao,
            repository = HomeboxRepository(
                ApiProvider(temp.newFolder("cache2"), ServerStatus().apply { report(true) }) { true }.apply {
                    updateConfig(ConnectionConfig(serverId = "s1", scheme = "http", host = server.hostName, port = server.port.toString(), token = "t"))
                },
            ),
            queueDir = temp.newFolder("queue2"),
            serverStatus = ServerStatus().apply { report(true) },
            isOnline = { true },
            // Start of the drain, then before each op: the user switches collection after the first.
            activeTarget = { if (calls++ < 2) target else target.copy(tenant = "other") },
            notificationsEnabled = { false },
            onDrainComplete = { _, _ -> },
        )

        switching.drain()

        assertEquals(listOf("/api/v1/entities/e1"), sentPaths())
        assertEquals("e2", dao.getAll().single().targetId)
    }

    @Test
    fun aRepeatFailureIsNotAnnouncedAgain() = runBlocking {
        queuePatch("e1")
        server.enqueue(error())
        server.enqueue(error())

        queue.drain()
        queue.drain()

        assertEquals(2, server.requestCount)
        assertEquals(listOf(0 to 1), drainResults)
    }
}
