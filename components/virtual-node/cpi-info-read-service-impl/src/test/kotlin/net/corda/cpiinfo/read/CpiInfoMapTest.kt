package net.corda.cpiinfo.read

import net.corda.cpiinfo.read.impl.CpiInfoMap
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

/**
 * Testing AVRO objects, so be sure to add `toAvro()`  (but using corda objects)
 */
class CpiInfoMapTest {
    private lateinit var map: CpiInfoMap

    private val secureHash = SecureHash("algorithm", "1234".toByteArray())

    private val currentTimestamp = Instant.now()

    @BeforeEach
    fun beforeEach() {
        map = CpiInfoMap()
    }

    @Test
    fun `put one CpiInfo`() {
        val identifier = CpiIdentifier("ghi", "hjk", secureHash)
        val metadata = CpiMetadata(identifier, secureHash, emptyList(), "", -1, currentTimestamp)

        map.put(identifier.toAvro(), metadata.toAvro())

        assertThat(map.get(identifier.toAvro())).isEqualTo(metadata.toAvro())
    }

    @Test
    fun `put two CpiInfo`() {
        val identifier = CpiIdentifier("ghi", "hjk", secureHash)
        val metadata = CpiMetadata(identifier, secureHash, emptyList(), "", -1, currentTimestamp)
        map.put(identifier.toAvro(), metadata.toAvro())


        val otherIdentifier = CpiIdentifier("abc", "def", secureHash)
        val otherMetadata = CpiMetadata(otherIdentifier, secureHash, emptyList(), "", -1, currentTimestamp)
        map.put(otherIdentifier.toAvro(), otherMetadata.toAvro())

        assertThat(map.get(identifier.toAvro())).isEqualTo(metadata.toAvro())
        assertThat(map.get(otherIdentifier.toAvro())).isEqualTo(otherMetadata.toAvro())
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

        assertThat(map.get(identifier.toAvro())).isEqualTo(metadata.toAvro())

        val actualCpiInfo = map.remove(identifier.toAvro())
        assertThat(actualCpiInfo).isEqualTo(metadata.toAvro())

        val cpiInfoAgain = map.remove(identifier.toAvro())
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
        assertThat(map.getAll()[0]).isEqualTo(metadata.toAvro())

        val otherIdentifier = CpiIdentifier("abc", "def", secureHash)
        val otherMetadata = CpiMetadata(otherIdentifier, secureHash, emptyList(), "", -1, currentTimestamp)
        map.put(otherIdentifier.toAvro(), otherMetadata.toAvro())

        all = map.getAll()
        assertThat(all).isNotNull
        assertThat(all.size).isEqualTo(2)
        assertThat(map.getAll()).contains(metadata.toAvro())
        assertThat(map.getAll()).contains(otherMetadata.toAvro())
    }

    @Test
    fun `test returning map as corda types`() {
        val keys = mutableListOf<net.corda.data.packaging.CpiIdentifier>()
        val count = 100

        // Add a number of CpiInfo objects to the map, and keep a copy of the keys
        for (i in 0..count) {
            val identifier = CpiIdentifier(UUID.randomUUID().toString(), "hjk", secureHash)
            val metadata = CpiMetadata(identifier, secureHash, emptyList(), "", -1, currentTimestamp)

            val key: net.corda.data.packaging.CpiIdentifier = identifier.toAvro()
            keys.add(key)
            map.put(key, metadata.toAvro())
        }

        // Check that we've added them
        for (i in 0..count) {
            assertThat(map.get(keys[i])).isNotNull
            // check id on the metadata object equals the key (id)
            assertThat(map.get(keys[i])!!.id!!).isEqualTo(keys[i])
        }

        // GET THE ENTIRE CONTENT OF THE MAP AS CORDA TYPES AND CHECK THAT TOO.
        val allCpiInfos = map.getAllAsCordaObjects()

        allCpiInfos.forEach { (k: CpiIdentifier, v: CpiMetadata) ->
            assertThat(map.get(k.toAvro())).isNotNull
            assertThat(map.get(k.toAvro())).isEqualTo(v.toAvro())
        }

        // Remove them
        for (i in 0..count) {
            val actualCpiInfo = map.remove(keys[i])
            assertThat(actualCpiInfo!!.id).isEqualTo(keys[i])
        }

        // Check they've been removed.
        for (i in 0..count) {
            val actualCpiInfo = map.remove(keys[i])
            assertThat(actualCpiInfo).isNull()
        }
    }
}
