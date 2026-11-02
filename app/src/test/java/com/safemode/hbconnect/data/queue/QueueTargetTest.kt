package com.safemode.hbconnect.data.queue

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueTargetTest {
    private fun op(server: String?, tenant: String?, account: String?) = QueuedOp(
        id = "op",
        kind = OpKind.UPDATE_ENTITY,
        tenant = tenant,
        serverId = server,
        accountId = account,
        payloadJson = "{}",
        targetId = "e1",
        photoPaths = emptyList(),
        label = "Edit",
        createdAt = 0L,
    )

    private val target = QueueTarget(server = "s1", tenant = "", account = "u1")

    @Test
    fun acceptsOpsFromTheSameServerCollectionAndAccount() {
        assertTrue(target.accepts(op("s1", "", "u1")))
        assertTrue(target.accepts(op("s1", null, "u1")))
    }

    @Test
    fun rejectsOpsFromAnotherServerCollectionOrAccount() {
        assertFalse(target.accepts(op("s2", "", "u1")))
        assertFalse(target.accepts(op("s1", "g2", "u1")))
        assertFalse(target.accepts(op("s1", "", "u2")))
    }

    @Test
    fun opsForAKnownAccountWaitWhileTheAccountIsUnknown() {
        assertFalse(target.copy(account = "").accepts(op("s1", "", "u1")))
    }

    @Test
    fun olderOpsWithoutServerOrAccountFollowTheActiveOne() {
        assertTrue(target.accepts(op(null, "", null)))
    }

    @Test
    fun sameScopeIgnoresReadiness() {
        assertTrue(target.sameScope(target.copy(ready = false)))
        assertFalse(target.sameScope(target.copy(account = "u2")))
    }
}
