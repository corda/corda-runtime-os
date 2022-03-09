package net.corda.virtualnode.impl

import net.corda.libs.packaging.CpiIdentifier
import net.corda.messaging.api.records.Record
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class VirtualNodeInfoProcessorTest {
    private lateinit var processor: VirtualNodeInfoProcessor
    private lateinit var listener: ListenerForTest

    private val secureHash = SecureHash("algorithm", "1234".toByteArray())

    @BeforeEach
    fun beforeEach() {
        listener = ListenerForTest()
        processor = VirtualNodeInfoProcessor({ /* don't care about the callback */ }, { /* don't care about callback */ })
    }

    private fun sendOnNextRandomMessage(processor: VirtualNodeInfoProcessor): HoldingIdentity {
        val holdingIdentity = HoldingIdentity("abc", UUID.randomUUID().toString())
        val newVirtualNodeInfo = VirtualNodeInfo(holdingIdentity, CpiIdentifier("ghi", "hjk", secureHash),
            null, UUID.randomUUID(), null, UUID.randomUUID())
        processor.onNext(Record("", holdingIdentity.toAvro(), newVirtualNodeInfo.toAvro()), null, emptyMap())
        return holdingIdentity
    }

    @Test
    fun `register client listener callback before onSnapshot is called`() {
        processor.registerCallback(listener)

        val holdingIdentity = HoldingIdentity("x500", "groupId")
        val virtualNodeInfo =
            VirtualNodeInfo(holdingIdentity, CpiIdentifier("name", "version", secureHash),
                null, UUID.randomUUID(), null, UUID.randomUUID())
        processor.onSnapshot(mapOf(holdingIdentity.toAvro() to virtualNodeInfo.toAvro()))

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(holdingIdentity))
        assertEquals(listener.lastSnapshot[holdingIdentity]?.holdingIdentity, holdingIdentity)
    }

    @Test
    fun `register client listener callback after onSnapshot is called`() {
        val holdingIdentity = HoldingIdentity("x500", "groupId")
        val virtualNodeInfo =
            VirtualNodeInfo(holdingIdentity, CpiIdentifier("name", "version", secureHash),
                null, UUID.randomUUID(), null, UUID.randomUUID())
        processor.onSnapshot(mapOf(holdingIdentity.toAvro() to virtualNodeInfo.toAvro()))

        processor.registerCallback(listener)

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(holdingIdentity))
        assertEquals(listener.lastSnapshot[holdingIdentity]?.holdingIdentity, holdingIdentity)
    }

    @Test
    fun `client listener callback executed onNext`() {
        processor.registerCallback(listener)
        processor.onSnapshot(emptyMap())

        assertTrue(listener.update)

        val holdingIdentity = HoldingIdentity("x500", "groupId")
        val virtualNodeInfo =
            VirtualNodeInfo(holdingIdentity, CpiIdentifier("name", "version", secureHash),
                null, UUID.randomUUID(), null, UUID.randomUUID())

        listener.update = false
        processor.onNext(Record("", holdingIdentity.toAvro(), virtualNodeInfo.toAvro()), null, emptyMap())

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(holdingIdentity))
        assertEquals(listener.lastSnapshot[holdingIdentity]?.holdingIdentity, holdingIdentity)

        listener.update = false

        val newHoldingIdentity = sendOnNextRandomMessage(processor)

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(newHoldingIdentity))
        assertEquals(listener.lastSnapshot[newHoldingIdentity]?.holdingIdentity, newHoldingIdentity)

        val anotherHoldingIdentity = sendOnNextRandomMessage(processor)

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(anotherHoldingIdentity))
        assertEquals(listener.lastSnapshot[anotherHoldingIdentity]?.holdingIdentity, anotherHoldingIdentity)

        assertEquals(3, listener.lastSnapshot.size, "Expected 3 updates")
    }

    @Test
    fun `unregister client listener callback`() {
        val closeable = processor.registerCallback(listener)

        val holdingIdentity = HoldingIdentity("x500", "groupId")
        val virtualNodeInfo =
            VirtualNodeInfo(holdingIdentity, CpiIdentifier("name", "version", secureHash),
                null, UUID.randomUUID(), null, UUID.randomUUID())
        processor.onSnapshot(mapOf(holdingIdentity.toAvro() to virtualNodeInfo.toAvro()))
        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(holdingIdentity))
        assertEquals(listener.lastSnapshot[holdingIdentity]?.holdingIdentity, holdingIdentity)

        // unregister and reset the listener
        closeable.close()
        listener.update = false

        val newHoldingIdentity = sendOnNextRandomMessage(processor)

        assertFalse(listener.update, "Listener should not have updated")
        assertTrue(listener.lastSnapshot.containsKey(holdingIdentity), "Listener should still contain last snapshot")
        assertEquals(
            listener.lastSnapshot[holdingIdentity]?.holdingIdentity,
            holdingIdentity,
            "Listener should still contain last snapshot"
        )
        assertFalse(listener.lastSnapshot.containsKey(newHoldingIdentity), "Listener should not have received update")
        assertNull(listener.lastSnapshot[newHoldingIdentity], "Listener should not contain new value for new key")
    }

    @Test
    fun `client listener callback persists between start and stop of service`() {
        processor.registerCallback(listener)
        processor.onSnapshot(emptyMap())

        assertTrue(listener.update)

        val holdingIdentity = HoldingIdentity("x500", "groupId")
        val virtualNodeInfo =
            VirtualNodeInfo(holdingIdentity, CpiIdentifier("name", "version", secureHash),
                null, UUID.randomUUID(), null, UUID.randomUUID())

        listener.update = false
        processor.onNext(Record("", holdingIdentity.toAvro(), virtualNodeInfo.toAvro()), null, emptyMap())

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(holdingIdentity))
        assertEquals(listener.lastSnapshot[holdingIdentity]?.holdingIdentity, holdingIdentity)

        listener.update = false

        val newHoldingIdentity = sendOnNextRandomMessage(processor)

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(newHoldingIdentity))
        assertEquals(listener.lastSnapshot[newHoldingIdentity]?.holdingIdentity, newHoldingIdentity)

        assertEquals(2, listener.lastSnapshot.size, "Expected two updates")
    }

    @Test
    fun `client listeners are unregistered when service closes`() {
        processor.registerCallback(listener)
        val holdingIdentity = HoldingIdentity("x500", "groupId")
        val virtualNodeInfo =
            VirtualNodeInfo(holdingIdentity, CpiIdentifier("name", "version", secureHash),
                null, UUID.randomUUID(), null, UUID.randomUUID())
        processor.onSnapshot(mapOf(holdingIdentity.toAvro() to virtualNodeInfo.toAvro()))

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(holdingIdentity))
        assertEquals(listener.lastSnapshot[holdingIdentity]?.holdingIdentity, holdingIdentity)

        processor.close()
        listener.update = false

        // listener should not now receive this message
        val newHoldingIdentity = sendOnNextRandomMessage(processor)

        assertFalse(listener.update, "Listener should not have updated")
        assertTrue(listener.lastSnapshot.containsKey(holdingIdentity), "Listener should still contain last snapshot")
        assertEquals(
            listener.lastSnapshot[holdingIdentity]?.holdingIdentity,
            holdingIdentity,
            "Listener should still contain last snapshot"
        )
        assertFalse(listener.lastSnapshot.containsKey(newHoldingIdentity), "Listener should not have received update")
        assertNull(listener.lastSnapshot[newHoldingIdentity], "Listener should not contain new value for new key")
    }

    @Test
    fun `can delete messages`() {
        processor.registerCallback(listener)
        processor.onSnapshot(emptyMap())
        assertTrue(listener.update)

        // Send message
        listener.update = false
        val holdingIdentity = HoldingIdentity("x500", "groupId")
        val virtualNodeInfo =
            VirtualNodeInfo(holdingIdentity, CpiIdentifier("name", "version", secureHash),
                null, UUID.randomUUID(), null, UUID.randomUUID())
        processor.onNext(Record("", holdingIdentity.toAvro(), virtualNodeInfo.toAvro()), null, emptyMap())

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(holdingIdentity))
        assertEquals(listener.lastSnapshot[holdingIdentity]?.holdingIdentity, holdingIdentity)
        assertEquals(1, listener.lastSnapshot.size, "No messages in snapshot")

        // Delete message
        listener.update = false
        processor.onNext(Record("", holdingIdentity.toAvro(), null), virtualNodeInfo.toAvro(), emptyMap())

        assertTrue(listener.update)
        assertTrue(!listener.lastSnapshot.containsKey(holdingIdentity))
        assertNull(listener.lastSnapshot[holdingIdentity]?.holdingIdentity)

        assertEquals(0, listener.lastSnapshot.size, "No messages in snapshot")
    }

    @Test
    fun `can get all`() {
        processor.registerCallback(listener)
        processor.onSnapshot(emptyMap())

        var virtualNodeList = processor.getAll()
        assertNotNull(virtualNodeList)
        assertTrue(virtualNodeList.isEmpty())

        val holdingIdentity = HoldingIdentity("x500", "groupId")
        val virtualNodeInfo =
            VirtualNodeInfo(holdingIdentity, CpiIdentifier("name", "version", secureHash),
                null, UUID.randomUUID(), null, UUID.randomUUID())
        val shortHash = holdingIdentity.id

        listener.update = false
        processor.onNext(Record("", holdingIdentity.toAvro(), virtualNodeInfo.toAvro()), null, emptyMap())

        // Get all again
        virtualNodeList = processor.getAll()
        assertEquals(1, virtualNodeList.size)
        assertEquals(shortHash, virtualNodeList[0].holdingIdentity.id)

        val newHoldingIdentity = sendOnNextRandomMessage(processor)
        val newShortHash = newHoldingIdentity.id
        assertNotEquals(shortHash, newShortHash)

        // Get all again
        virtualNodeList = processor.getAll()
        assertEquals(2, virtualNodeList.size)
        val shortHashList = virtualNodeList.map{ it.holdingIdentity.id }
        assertTrue(shortHashList.contains(shortHash))
        assertTrue(shortHashList.contains(newShortHash))
    }

    @Test
    fun `can get by short hash id`() {
        processor.registerCallback(listener)
        processor.onSnapshot(emptyMap())

        val holdingIdentity = HoldingIdentity("x500", "groupId")
        val virtualNodeInfo =
            VirtualNodeInfo(holdingIdentity, CpiIdentifier("name", "version", secureHash),
                null, UUID.randomUUID(), null, UUID.randomUUID())

        listener.update = false
        processor.onNext(Record("", holdingIdentity.toAvro(), virtualNodeInfo.toAvro()), null, emptyMap())

        // Get by short hash
        val shortHash = holdingIdentity.id
        val actualVirtualNodeInfo = processor.getById(shortHash)
        assertEquals(holdingIdentity, actualVirtualNodeInfo!!.holdingIdentity)

        val newHoldingIdentity = sendOnNextRandomMessage(processor)
        val newShortHash = newHoldingIdentity.id
        assertNotEquals(shortHash, newShortHash)

        // Get by short hash again
        val actualNewVirtualNodeInfo = processor.getById(newShortHash)
        assertEquals(newHoldingIdentity, actualNewVirtualNodeInfo!!.holdingIdentity)

        val anotherHoldingIdentity = sendOnNextRandomMessage(processor)
        val anotherShortHash = anotherHoldingIdentity.id
        assertNotEquals(shortHash, anotherShortHash)

        // Get by short hash again
        val actualAnotherHoldingIdentity = processor.getById(anotherShortHash)
        assertEquals(anotherHoldingIdentity, actualAnotherHoldingIdentity!!.holdingIdentity)
    }

    @Test
    fun `clear message processor`() {
        val processor = VirtualNodeInfoProcessor({ /* don't care about callback */ }, { /* don't care about callback */ })
        val holdingIdentity = HoldingIdentity("abc", UUID.randomUUID().toString())
        val virtualNodeInfo = VirtualNodeInfo(holdingIdentity, CpiIdentifier("ghi", "hjk", secureHash),
            null, UUID.randomUUID(), null, UUID.randomUUID())
        processor.registerCallback(listener)
        processor.onSnapshot(mapOf(holdingIdentity.toAvro() to virtualNodeInfo.toAvro()))

        assertThat(listener.lastSnapshot.containsKey(holdingIdentity)).isTrue

        processor.clear()

        assertThat(listener.lastSnapshot.isEmpty()).isTrue
        assertThat(listener.changedKeys.contains(holdingIdentity)).isTrue
    }

    @Test
    fun `internal onSnapshot callback is called`() {
        var onSnapshot = false
        val processor = VirtualNodeInfoProcessor({ onSnapshot = true }, { /* don't care about callback */ })
        val holdingIdentity = HoldingIdentity("abc", UUID.randomUUID().toString())
        val virtualNodeInfo = VirtualNodeInfo(holdingIdentity, CpiIdentifier("ghi", "hjk", secureHash),
            null, UUID.randomUUID(), null, UUID.randomUUID())

        processor.registerCallback(listener)

        assertThat(onSnapshot).isFalse
        processor.onSnapshot(mapOf(holdingIdentity.toAvro() to virtualNodeInfo.toAvro()))
        assertThat(onSnapshot).isTrue
    }

    @Test
    fun `internal onError callback is called`() {
        var onError = false
        val processor = VirtualNodeInfoProcessor({ /* don't care */ }, { onError = true })
        val holdingIdentity = HoldingIdentity("abc", UUID.randomUUID().toString())
        val holdingIdentityOther = HoldingIdentity("def", UUID.randomUUID().toString())
        val virtualNodeInfo = VirtualNodeInfo(holdingIdentity, CpiIdentifier("ghi", "hjk", secureHash),
            null, UUID.randomUUID(), null, UUID.randomUUID())

        processor.registerCallback(listener)

        assertThat(onError).isFalse
        processor.onSnapshot(mapOf(holdingIdentityOther.toAvro() to virtualNodeInfo.toAvro()))
        assertThat(onError).isTrue
    }
}
