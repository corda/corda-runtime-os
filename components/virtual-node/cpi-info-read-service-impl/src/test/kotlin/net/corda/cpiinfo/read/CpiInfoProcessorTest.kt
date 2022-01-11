package net.corda.cpiinfo.read

import net.corda.cpiinfo.read.impl.CpiInfoReaderProcessor
import net.corda.messaging.api.records.Record
import net.corda.packaging.CPI
import net.corda.packaging.converters.toAvro
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class CpiInfoProcessorTest {
    private lateinit var processor: CpiInfoReaderProcessor
    private lateinit var listener: ListenerForTest

    private val secureHash = SecureHash("algorithm", "1234".toByteArray())

    @BeforeEach
    fun beforeEach() {
        listener = ListenerForTest()
        processor = CpiInfoReaderProcessor({ /* don't care about the callback */ }, { /* don't care about callback */ })
    }

    private fun sendOnNextRandomMessage(processor: CpiInfoReaderProcessor): CPI.Identifier {
        val cpiIdentifier = CPI.Identifier.newInstance("abc", UUID.randomUUID().toString(), secureHash)
        val cpiMetadata = CPI.Metadata.newInstance(cpiIdentifier, secureHash, emptyList(), "group policy")
        processor.onNext(Record("", cpiMetadata.id.toAvro(), cpiMetadata.toAvro()), null, emptyMap())
        return cpiIdentifier
    }

    @Test
    fun `register client listener callback before onSnapshot is called`() {
        processor.registerCallback(listener)

        val cpiIdentifier = CPI.Identifier.newInstance("abc", UUID.randomUUID().toString(), secureHash)
        val cpiMetadata = CPI.Metadata.newInstance(cpiIdentifier, secureHash, emptyList(), "group policy")
        processor.onSnapshot(mapOf(cpiIdentifier.toAvro() to cpiMetadata.toAvro()))

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(cpiIdentifier))
        assertEquals(listener.lastSnapshot[cpiIdentifier]?.id, cpiIdentifier)
    }

    @Test
    fun `register client listener callback after onSnapshot is called`() {
        val cpiIdentifier = CPI.Identifier.newInstance("abc", UUID.randomUUID().toString(), secureHash)
        val cpiMetadata = CPI.Metadata.newInstance(cpiIdentifier, secureHash, emptyList(), "group policy")

        processor.onSnapshot(mapOf(cpiIdentifier.toAvro() to cpiMetadata.toAvro()))

        processor.registerCallback(listener)

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(cpiIdentifier))
        assertEquals(listener.lastSnapshot[cpiIdentifier]?.id, cpiIdentifier)
    }

    @Test
    fun `client listener callback executed onNext`() {
        processor.registerCallback(listener)
        processor.onSnapshot(emptyMap())

        assertTrue(listener.update)

        val cpiIdentifier = CPI.Identifier.newInstance("abc", UUID.randomUUID().toString(), secureHash)
        val cpiMetadata = CPI.Metadata.newInstance(cpiIdentifier, secureHash, emptyList(), "group policy")

        listener.update = false
        processor.onNext(Record("", cpiIdentifier.toAvro(), cpiMetadata.toAvro()), null, emptyMap())

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(cpiIdentifier))
        assertEquals(listener.lastSnapshot[cpiIdentifier]?.id, cpiIdentifier)

        listener.update = false

        val newHoldingIdentity = sendOnNextRandomMessage(processor)

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(newHoldingIdentity))
        assertEquals(listener.lastSnapshot[newHoldingIdentity]?.id, newHoldingIdentity)

        val anotherHoldingIdentity = sendOnNextRandomMessage(processor)

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(anotherHoldingIdentity))
        assertEquals(listener.lastSnapshot[anotherHoldingIdentity]?.id, anotherHoldingIdentity)

        assertEquals(3, listener.lastSnapshot.size, "Expected 3 updates")
    }

    @Test
    fun `unregister client listener callback`() {
        val closeable = processor.registerCallback(listener)

        val cpiIdentifier = CPI.Identifier.newInstance("abc", UUID.randomUUID().toString(), secureHash)
        val cpiMetadata = CPI.Metadata.newInstance(cpiIdentifier, secureHash, emptyList(), "group policy")

        processor.onSnapshot(mapOf(cpiIdentifier.toAvro() to cpiMetadata.toAvro()))
        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(cpiIdentifier))
        assertEquals(listener.lastSnapshot[cpiIdentifier]?.id, cpiIdentifier)

        // unregister and reset the listener
        closeable.close()
        listener.update = false

        val newHoldingIdentity = sendOnNextRandomMessage(processor)

        assertFalse(listener.update, "Listener should not have updated")
        assertTrue(listener.lastSnapshot.containsKey(cpiIdentifier), "Listener should still contain last snapshot")
        assertEquals(
            listener.lastSnapshot[cpiIdentifier]?.id,
            cpiIdentifier,
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

        val cpiIdentifier = CPI.Identifier.newInstance("abc", UUID.randomUUID().toString(), secureHash)
        val cpiMetadata = CPI.Metadata.newInstance(cpiIdentifier, secureHash, emptyList(), "group policy")


        listener.update = false
        processor.onNext(Record("", cpiIdentifier.toAvro(), cpiMetadata.toAvro()), null, emptyMap())

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(cpiIdentifier))
        assertEquals(listener.lastSnapshot[cpiIdentifier]?.id, cpiIdentifier)

        listener.update = false

        val newHoldingIdentity = sendOnNextRandomMessage(processor)

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(newHoldingIdentity))
        assertEquals(listener.lastSnapshot[newHoldingIdentity]?.id, newHoldingIdentity)

        assertEquals(2, listener.lastSnapshot.size, "Expected two updates")
    }

    @Test
    fun `client listeners are unregistered when service closes`() {
        processor.registerCallback(listener)
        val cpiIdentifier = CPI.Identifier.newInstance("abc", UUID.randomUUID().toString(), secureHash)
        val cpiMetadata = CPI.Metadata.newInstance(cpiIdentifier, secureHash, emptyList(), "group policy")

        processor.onSnapshot(mapOf(cpiIdentifier.toAvro() to cpiMetadata.toAvro()))

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(cpiIdentifier))
        assertEquals(listener.lastSnapshot[cpiIdentifier]?.id, cpiIdentifier)

        processor.close()
        listener.update = false

        // listener should not now receive this message
        val newHoldingIdentity = sendOnNextRandomMessage(processor)

        assertFalse(listener.update, "Listener should not have updated")
        assertTrue(listener.lastSnapshot.containsKey(cpiIdentifier), "Listener should still contain last snapshot")
        assertEquals(
            listener.lastSnapshot[cpiIdentifier]?.id,
            cpiIdentifier,
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
        val cpiIdentifier = CPI.Identifier.newInstance("abc", UUID.randomUUID().toString(), secureHash)
        val cpiMetadata = CPI.Metadata.newInstance(cpiIdentifier, secureHash, emptyList(), "group policy")

        processor.onNext(Record("", cpiIdentifier.toAvro(), cpiMetadata.toAvro()), null, emptyMap())

        assertTrue(listener.update)
        assertTrue(listener.lastSnapshot.containsKey(cpiIdentifier))
        assertEquals(listener.lastSnapshot[cpiIdentifier]?.id, cpiIdentifier)
        assertEquals(1, listener.lastSnapshot.size, "No messages in snapshot")

        // Delete message
        listener.update = false
        processor.onNext(Record("", cpiIdentifier.toAvro(), null), cpiMetadata.toAvro(), emptyMap())

        assertTrue(listener.update)
        assertTrue(!listener.lastSnapshot.containsKey(cpiIdentifier))
        assertNull(listener.lastSnapshot[cpiIdentifier]?.id)

        assertEquals(0, listener.lastSnapshot.size, "No messages in snapshot")
    }

    @Test
    fun `clear message processor`() {
        val processor = CpiInfoReaderProcessor({ /* don't care about callback */ }, { /* don't care about callback */ })
        val cpiIdentifier = CPI.Identifier.newInstance("abc", UUID.randomUUID().toString(), secureHash)
        val cpiMetadata = CPI.Metadata.newInstance(cpiIdentifier, secureHash, emptyList(), "group policy")

        processor.registerCallback(listener)
        processor.onSnapshot(mapOf(cpiIdentifier.toAvro() to cpiMetadata.toAvro()))

        assertThat(listener.lastSnapshot.containsKey(cpiIdentifier)).isTrue

        processor.clear()

        assertThat(listener.lastSnapshot.isEmpty()).isTrue
        assertThat(listener.changedKeys.contains(cpiIdentifier)).isTrue
    }

    @Test
    fun `internal onSnapshot callback is called`() {
        var onSnapshot = false
        val processor = CpiInfoReaderProcessor({ onSnapshot = true }, { /* don't care about callback */ })
        val cpiIdentifier = CPI.Identifier.newInstance("abc", UUID.randomUUID().toString(), secureHash)
        val cpiMetadata = CPI.Metadata.newInstance(cpiIdentifier, secureHash, emptyList(), "group policy")

        processor.registerCallback(listener)

        assertThat(onSnapshot).isFalse
        processor.onSnapshot(mapOf(cpiIdentifier.toAvro() to cpiMetadata.toAvro()))
        assertThat(onSnapshot).isTrue
    }

    @Test
    fun `internal onError callback is called`() {
        var onError = false
        val processor = CpiInfoReaderProcessor({ /* don't care */ }, { onError = true })
        val cpiIdentifier = CPI.Identifier.newInstance("abc", UUID.randomUUID().toString(), secureHash)
        val cpiIdentifierOther = CPI.Identifier.newInstance("def", UUID.randomUUID().toString(), secureHash)
        val cpiMetadata = CPI.Metadata.newInstance(cpiIdentifier, secureHash, emptyList(), "group policy")

        processor.registerCallback(listener)

        assertThat(onError).isFalse
        processor.onSnapshot(mapOf(cpiIdentifierOther.toAvro() to cpiMetadata.toAvro()))
        assertThat(onError).isTrue
    }
}
