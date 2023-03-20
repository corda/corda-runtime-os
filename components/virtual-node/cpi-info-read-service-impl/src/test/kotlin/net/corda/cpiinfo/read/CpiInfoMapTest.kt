package net.corda.cpiinfo.read

import net.corda.cpiinfo.read.impl.CpiInfoMap
import net.corda.crypto.core.SecureHashImpl
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class CpiInfoMapTest {
    private lateinit var map: CpiInfoMap

    private val secureHash = SecureHashImpl("algorithm", "1234".toByteArray())

    private val currentTimestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    @BeforeEach
    fun beforeEach() {
        map = CpiInfoMap()
    }

    @Test
    fun `put one CpiInfo`() {
        val identifier = CpiIdentifier("ghi", "hjk", secureHash)
        val metadata = CpiMetadata(identifier, secureHash, emptyList(), "", -1, currentTimestamp)

        map.put(identifier.toAvro(), metadata.toAvro())

        assertThat(map.get(identifier)).isEqualTo(metadata)
    }

    @Test
    fun `put two CpiInfo`() {
        val identifier = CpiIdentifier("ghi", "hjk", secureHash)
        val metadata = CpiMetadata(identifier, secureHash, emptyList(), "", -1, currentTimestamp)
        map.put(identifier.toAvro(), metadata.toAvro())


        val otherIdentifier = CpiIdentifier("abc", "def", secureHash)
        val otherMetadata = CpiMetadata(otherIdentifier, secureHash, emptyList(), "", -1, currentTimestamp)
        map.put(otherIdentifier.toAvro(), otherMetadata.toAvro())

        assertThat(map.get(identifier)).isEqualTo(metadata)
        assertThat(map.get(otherIdentifier)).isEqualTo(otherMetadata)
    }

    /**
     * This scenario should NEVER occur in production, and should be caught in development.
     */
    @Test
    fun `putting mismatched CPI Identifiers throws`() {
        val identifier = CpiIdentifier("ghi", "hjk", secureHash)
        val differentIdentifier = CpiIdentifier("abc", "def", secureHash)
        val metadata = CpiMetadata(differentIdentifier, secureHash, emptyList(), "", -1, currentTimestamp)
        assertThrows<IllegalArgumentException> {
            map.put(identifier.toAvro(), metadata.toAvro())
        }
    }

    @Test
    fun `put one and remove one CpiInfo`() {
        val identifier = CpiIdentifier("ghi", "hjk", secureHash)
        val metadata = CpiMetadata(identifier, secureHash, emptyList(), "", -1, currentTimestamp)
        map.put(identifier.toAvro(), metadata.toAvro())

        assertThat(map.get(identifier)).isEqualTo(metadata)

        val actualCpiInfo = map.remove(identifier)
        assertThat(actualCpiInfo).isEqualTo(metadata)

        val cpiInfoAgain = map.remove(identifier)
        assertThat(cpiInfoAgain).isNull()
    }

    @Test
    fun `test get all CpiInfo`() {
        val identifier = CpiIdentifier("ghi", "hjk", secureHash)
        val metadata = CpiMetadata(identifier, secureHash, emptyList(), "", -1, currentTimestamp)
        map.put(identifier.toAvro(), metadata.toAvro())

        var all = map.getAll()
        assertThat(all).isNotNull
        assertThat(all.size).isEqualTo(1)
        assertThat(map.getAll().values.first()).isEqualTo(metadata)

        val otherIdentifier = CpiIdentifier("abc", "def", secureHash)
        val otherMetadata = CpiMetadata(otherIdentifier, secureHash, emptyList(), "", -1, currentTimestamp)
        map.put(otherIdentifier.toAvro(), otherMetadata.toAvro())

        all = map.getAll()
        assertThat(all).isNotNull
        assertThat(all.size).isEqualTo(2)
        assertThat(map.getAll().values).contains(metadata)
        assertThat(map.getAll().values).contains(otherMetadata)
    }

    @Test
    fun `test returning map as corda types`() {
        val keys = mutableListOf<CpiIdentifier>()
        val count = 100

        // Add a number of CpiInfo objects to the map, and keep a copy of the keys
        for (i in 0..count) {
            val identifier = CpiIdentifier(UUID.randomUUID().toString(), "hjk", secureHash)
            val metadata = CpiMetadata(identifier, secureHash, emptyList(), "", -1, currentTimestamp)

            keys.add(identifier)
            map.put(identifier.toAvro(), metadata.toAvro())
        }

        // Check that we've added them
        for (i in 0..count) {
            assertThat(map.get(keys[i])).isNotNull
            // check id on the metadata object equals the key (id)
            assertThat(map.get(keys[i])?.cpiId).isEqualTo(keys[i])
        }

        // GET THE ENTIRE CONTENT OF THE MAP AS CORDA TYPES AND CHECK THAT TOO.
        val allCpiInfos = map.getAll()

        allCpiInfos.forEach { (k: CpiIdentifier, v: CpiMetadata) ->
            assertThat(map.get(k)).isNotNull
            assertThat(map.get(k)).isEqualTo(v)
        }

        // Remove them
        for (i in 0..count) {
            val actualCpiInfo = map.remove(keys[i])
            assertThat(actualCpiInfo?.cpiId).isEqualTo(keys[i])
        }

        // Check they've been removed.
        for (i in 0..count) {
            val actualCpiInfo = map.remove(keys[i])
            assertThat(actualCpiInfo).isNull()
        }
    }
}
