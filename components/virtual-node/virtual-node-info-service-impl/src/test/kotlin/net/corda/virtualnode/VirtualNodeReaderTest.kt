package net.corda.virtualnode

import net.corda.configuration.read.ConfigKeys
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.packaging.CPI
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.impl.VirtualNodeInfoProcessorImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import java.util.UUID

class VirtualNodeReaderTest {
    private lateinit var processor: VirtualNodeInfoProcessorImpl
    private lateinit var listener: ListenerForTest
    private val subscriptionFactory: SubscriptionFactory = mock()
    private val subscription: CompactedSubscription<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo> =
        mock()

    private val secureHash = SecureHash("algorithm", "1".toByteArray())

    @BeforeEach
    fun beforeEach() {
        listener = ListenerForTest()
        processor = VirtualNodeInfoProcessorImpl(subscriptionFactory)

        Mockito.`when`(
            subscriptionFactory.createCompactedSubscription(
                any(),
                any<CompactedProcessor<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>(),
                any()
            )
        ).thenReturn(subscription)

        Mockito.doNothing().`when`(subscription).start()
        Mockito.doNothing().`when`(subscription).stop()

        // Should do nothing, and is idempotent.
        processor.start()
    }

    private fun sendOnNextRandomMessage(): HoldingIdentity {
        val holdingIdentity = HoldingIdentity("abc", UUID.randomUUID().toString())
        val newVirtualNodeInfo = VirtualNodeInfo(holdingIdentity, CPI.Identifier.newInstance("ghi", "hjk", secureHash))
        processor.onNext(Record("", holdingIdentity.toAvro(), newVirtualNodeInfo.toAvro()), null, emptyMap())
        return holdingIdentity
    }

    @Test
    fun `register callback before onSnapshot is called`() {
        processor.registerCallback(listener)

        val holdingIdentity = HoldingIdentity("x500", "groupId")
        val virtualNodeInfo =
            VirtualNodeInfo(holdingIdentity, CPI.Identifier.newInstance("name", "version", secureHash))
        processor.onSnapshot(mapOf(holdingIdentity.toAvro() to virtualNodeInfo.toAvro()))

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(holdingIdentity))
        assertEquals(listener.lastSnapshot[holdingIdentity]?.holdingIdentity, holdingIdentity)
    }

    @Test
    fun `register callback after onSnapshot is called`() {
        val holdingIdentity = HoldingIdentity("x500", "groupId")
        val virtualNodeInfo =
            VirtualNodeInfo(holdingIdentity, CPI.Identifier.newInstance("name", "version", secureHash))
        processor.onSnapshot(mapOf(holdingIdentity.toAvro() to virtualNodeInfo.toAvro()))

        processor.registerCallback(listener)

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(holdingIdentity))
        assertEquals(listener.lastSnapshot[holdingIdentity]?.holdingIdentity, holdingIdentity)
    }

    @Test
    fun `callback executed onNext`() {
        processor.registerCallback(listener)
        processor.onSnapshot(emptyMap())

        assertTrue(listener.update)

        val holdingIdentity = HoldingIdentity("x500", "groupId")
        val virtualNodeInfo =
            VirtualNodeInfo(holdingIdentity, CPI.Identifier.newInstance("name", "version", secureHash))

        listener.update = false
        processor.onNext(Record("", holdingIdentity.toAvro(), virtualNodeInfo.toAvro()), null, emptyMap())

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(holdingIdentity))
        assertEquals(listener.lastSnapshot[holdingIdentity]?.holdingIdentity, holdingIdentity)

        listener.update = false

        val newHoldingIdentity = sendOnNextRandomMessage()

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(newHoldingIdentity))
        assertEquals(listener.lastSnapshot[newHoldingIdentity]?.holdingIdentity, newHoldingIdentity)

        val anotherHoldingIdentity = sendOnNextRandomMessage()

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(anotherHoldingIdentity))
        assertEquals(listener.lastSnapshot[anotherHoldingIdentity]?.holdingIdentity, anotherHoldingIdentity)

        assertEquals(3, listener.lastSnapshot.size, "Expected 3 updates")
    }

    @Test
    fun `unregister callback`() {
        val closeable = processor.registerCallback(listener)

        val holdingIdentity = HoldingIdentity("x500", "groupId")
        val virtualNodeInfo =
            VirtualNodeInfo(holdingIdentity, CPI.Identifier.newInstance("name", "version", secureHash))
        processor.onSnapshot(mapOf(holdingIdentity.toAvro() to virtualNodeInfo.toAvro()))
        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(holdingIdentity))
        assertEquals(listener.lastSnapshot[holdingIdentity]?.holdingIdentity, holdingIdentity)

        // unregister and reset the listener
        closeable.close()
        listener.update = false

        val newHoldingIdentity = sendOnNextRandomMessage()

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
    fun `callback persists between start and stop of service`() {
        processor.start()

        processor.registerCallback(listener)
        processor.onSnapshot(emptyMap())

        assertTrue(listener.update)

        val holdingIdentity = HoldingIdentity("x500", "groupId")
        val virtualNodeInfo =
            VirtualNodeInfo(holdingIdentity, CPI.Identifier.newInstance("name", "version", secureHash))

        listener.update = false
        processor.onNext(Record("", holdingIdentity.toAvro(), virtualNodeInfo.toAvro()), null, emptyMap())

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(holdingIdentity))
        assertEquals(listener.lastSnapshot[holdingIdentity]?.holdingIdentity, holdingIdentity)

        listener.update = false

        processor.stop()
        processor.start()

        val newHoldingIdentity = sendOnNextRandomMessage()

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(newHoldingIdentity))
        assertEquals(listener.lastSnapshot[newHoldingIdentity]?.holdingIdentity, newHoldingIdentity)

        assertEquals(2, listener.lastSnapshot.size, "Expected two updates")
    }

    @Test
    fun `listeners are unregistered when service closes`() {
        processor.start()
        processor.registerCallback(listener)
        val holdingIdentity = HoldingIdentity("x500", "groupId")
        val virtualNodeInfo =
            VirtualNodeInfo(holdingIdentity, CPI.Identifier.newInstance("name", "version", secureHash))
        processor.onSnapshot(mapOf(holdingIdentity.toAvro() to virtualNodeInfo.toAvro()))

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(holdingIdentity))
        assertEquals(listener.lastSnapshot[holdingIdentity]?.holdingIdentity, holdingIdentity)

        processor.close()
        processor.start()
        listener.update = false

        // listener should not now receive this message
        val newHoldingIdentity = sendOnNextRandomMessage()

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
            VirtualNodeInfo(holdingIdentity, CPI.Identifier.newInstance("name", "version", secureHash))
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
    fun `can get by short hash id`() {
        processor.registerCallback(listener)
        processor.onNewConfiguration(setOf(ConfigKeys.MESSAGING_KEY), mapOf(ConfigKeys.MESSAGING_KEY to SmartConfigImpl.empty()) )

        processor.onSnapshot(emptyMap())

        val holdingIdentity = HoldingIdentity("x500", "groupId")
        val virtualNodeInfo =
            VirtualNodeInfo(holdingIdentity, CPI.Identifier.newInstance("name", "version", secureHash))

        listener.update = false
        processor.onNext(Record("", holdingIdentity.toAvro(), virtualNodeInfo.toAvro()), null, emptyMap())

        // Get by short hash
        val shortHash = holdingIdentity.id
        val actualVirtualNodeInfo = processor.getById(shortHash)
        assertEquals(holdingIdentity, actualVirtualNodeInfo!!.holdingIdentity)

        val newHoldingIdentity = sendOnNextRandomMessage()
        val newShortHash = newHoldingIdentity.id
        assertNotEquals(shortHash, newShortHash)

        // Get by short hash again
        val actualNewVirtualNodeInfo = processor.getById(newShortHash)
        assertEquals(newHoldingIdentity, actualNewVirtualNodeInfo!!.holdingIdentity)

        val anotherHoldingIdentity = sendOnNextRandomMessage()
        val anotherShortHash = anotherHoldingIdentity.id
        assertNotEquals(shortHash, anotherShortHash)

        // Get by short hash again
        val actualAnotherHoldingIdentity = processor.getById(anotherShortHash)
        assertEquals(anotherHoldingIdentity, actualAnotherHoldingIdentity!!.holdingIdentity)
    }
}
