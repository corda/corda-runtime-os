package net.corda.cpiinfo.read

import net.corda.cpiinfo.read.impl.CpiInfoReaderProcessor
import net.corda.crypto.core.SecureHashImpl
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class CpiInfoProcessorTest {
    private lateinit var processor: CpiInfoReaderProcessor

    private val secureHash = SecureHashImpl("algorithm", "1234".toByteArray())

    private val currentTimestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    @BeforeEach
    fun beforeEach() {
        processor = CpiInfoReaderProcessor({ /* don't care about the callback */ }, { /* don't care about callback */ })
    }

    @Test
    fun `can get messages`() {
        processor.onSnapshot(emptyMap())

        // Send message
        val cpiIdentifier = CpiIdentifier("abc", UUID.randomUUID().toString(), secureHash)
        val cpiMetadata = CpiMetadata(cpiIdentifier, secureHash, emptyList(), "group policy", -1, currentTimestamp)

        assertNull(processor.get(cpiIdentifier))

        processor.onNext(Record("", cpiIdentifier.toAvro(), cpiMetadata.toAvro()), null, emptyMap())

        assertNotNull(processor.get(cpiIdentifier))
    }

    @Test
    fun `can get all messages`() {
        processor.onSnapshot(emptyMap())

        val processor = CpiInfoReaderProcessor({ /* don't care about callback */ }, { /* don't care about callback */ })
        val cpiIdentifier = CpiIdentifier("abc", UUID.randomUUID().toString(), secureHash)
        val cpiMetadata = CpiMetadata(cpiIdentifier, secureHash, emptyList(), "group policy", -1, currentTimestamp)

        processor.onSnapshot(mapOf(cpiIdentifier.toAvro() to cpiMetadata.toAvro()))

        assertEquals(processor.getAll().size, 1)
        assertEquals(processor.getAll().single{ it.cpiId == cpiIdentifier }, cpiMetadata)
    }

    @Test
    fun `messages map is updated`() {
        processor.onSnapshot(emptyMap())

        val processor = CpiInfoReaderProcessor({ /* don't care about callback */ }, { /* don't care about callback */ })
        val cpiIdentifier = CpiIdentifier("abc", UUID.randomUUID().toString(), secureHash)
        val cpiMetadata = CpiMetadata(cpiIdentifier, secureHash, emptyList(), "group policy", -1, currentTimestamp)

        processor.onSnapshot(mapOf(cpiIdentifier.toAvro() to cpiMetadata.toAvro()))

        assertEquals(processor.getAll().size, 1)

        // Send message
        val cpiIdentifier2 = CpiIdentifier("abc2", UUID.randomUUID().toString(), secureHash)
        val cpiMetadata2 = CpiMetadata(cpiIdentifier2, secureHash, emptyList(), "group policy2", -1, currentTimestamp)

        processor.onNext(Record("", cpiIdentifier2.toAvro(), cpiMetadata2.toAvro()), null, emptyMap())

        assertEquals(processor.getAll().size, 2)
        assertEquals(processor.get(cpiIdentifier2), cpiMetadata2)
    }

    @Test
    fun `can delete messages`() {
        processor.onSnapshot(emptyMap())

        // Send message
        val cpiIdentifier = CpiIdentifier("abc", UUID.randomUUID().toString(), secureHash)
        val cpiMetadata = CpiMetadata(cpiIdentifier, secureHash, emptyList(), "group policy", -1, currentTimestamp)

        processor.onNext(Record("", cpiIdentifier.toAvro(), cpiMetadata.toAvro()), null, emptyMap())

        assertTrue(processor.getAll().any { it.cpiId == cpiIdentifier })

        // Delete message
        processor.onNext(Record("", cpiIdentifier.toAvro(), null), cpiMetadata.toAvro(), emptyMap())

        assertFalse(processor.getAll().any { it.cpiId == cpiIdentifier })
    }

    @Test
    fun `clear message processor`() {
        val processor = CpiInfoReaderProcessor({ /* don't care about callback */ }, { /* don't care about callback */ })
        val cpiIdentifier = CpiIdentifier("abc", UUID.randomUUID().toString(), secureHash)
        val cpiMetadata = CpiMetadata(cpiIdentifier, secureHash, emptyList(), "group policy", -1, currentTimestamp)

        processor.onSnapshot(mapOf(cpiIdentifier.toAvro() to cpiMetadata.toAvro()))

        assertTrue(processor.getAll().any { it.cpiId == cpiIdentifier })

        processor.clear()

        assertEquals(0, processor.getAll().size, "No messages in snapshot")
    }

    @Test
    fun `onError callback is called from snapshot`() {
        var onError = false
        val processor = CpiInfoReaderProcessor({ /* don't care */ }, { onError = true })
        val cpiIdentifier = CpiIdentifier("abc", UUID.randomUUID().toString(), secureHash)
        val cpiIdentifierOther = CpiIdentifier("def", UUID.randomUUID().toString(), secureHash)
        val cpiMetadata = CpiMetadata(cpiIdentifier, secureHash, emptyList(), "group policy", -1, currentTimestamp)

        Assertions.assertThat(onError).isFalse
        processor.onSnapshot(mapOf(cpiIdentifierOther.toAvro() to cpiMetadata.toAvro()))
        Assertions.assertThat(onError).isTrue
    }

    @Test
    fun `onError callback is called from next`() {
        var onError = false
        val processor = CpiInfoReaderProcessor({ /* don't care */ }, { onError = true })
        val cpiIdentifier = CpiIdentifier("abc", UUID.randomUUID().toString(), secureHash)
        val cpiIdentifierOther = CpiIdentifier("def", UUID.randomUUID().toString(), secureHash)
        val cpiMetadata = CpiMetadata(cpiIdentifier, secureHash, emptyList(), "group policy", -1, currentTimestamp)

        Assertions.assertThat(onError).isFalse
        processor.onNext(Record("", cpiIdentifierOther.toAvro(), cpiMetadata.toAvro()), null, emptyMap())
        Assertions.assertThat(onError).isTrue
    }

    @Test
    fun `onStatusUp callback is called from snapshot`() {
        processor.onSnapshot(emptyMap())

        var onStatusUp = false
        val processor = CpiInfoReaderProcessor({ onStatusUp = true }, { /* don't care about callback */ })
        val cpiIdentifier = CpiIdentifier("abc", UUID.randomUUID().toString(), secureHash)
        val cpiMetadata = CpiMetadata(cpiIdentifier, secureHash, emptyList(), "group policy", -1, currentTimestamp)

        Assertions.assertThat(onStatusUp).isFalse
        processor.onSnapshot(mapOf(cpiIdentifier.toAvro() to cpiMetadata.toAvro()))
        Assertions.assertThat(onStatusUp).isTrue
    }
}
