package net.corda.cpiinfo.impl

import net.corda.cpiinfo.read.impl.CpiInfoMap
import net.corda.data.packaging.CPIIdentifier
import net.corda.packaging.CPI
import net.corda.packaging.converters.toAvro
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

/**
 * Testing AVRO objects, so be sure to add `toAvro()`  (but using corda objects)
 */
class CpiInfoMapTest {
    private lateinit var map: CpiInfoMap

    private val secureHash = SecureHash("algorithm", "1234".toByteArray())

    @BeforeEach
    fun beforeEach() {
        map = CpiInfoMap()
    }

    @Test
    fun `put one CpiInfo`() {
        val identifier = CPI.Identifier.newInstance("ghi", "hjk", secureHash)
        val metadata = CPI.Metadata.newInstance(identifier, secureHash, emptyList(), "")

        map.put(identifier.toAvro(), metadata.toAvro())

        assertThat(map.get(identifier.toAvro())).isEqualTo(metadata.toAvro())
    }

    @Test
    fun `put two CpiInfo`() {
        val identifier = CPI.Identifier.newInstance("ghi", "hjk", secureHash)
        val metadata = CPI.Metadata.newInstance(identifier, secureHash, emptyList(), "")
        map.put(identifier.toAvro(), metadata.toAvro())


        val otherIdentifier = CPI.Identifier.newInstance("abc", "def", secureHash)
        val otherMetadata = CPI.Metadata.newInstance(otherIdentifier, secureHash, emptyList(), "")
        map.put(otherIdentifier.toAvro(), otherMetadata.toAvro())

        assertThat(map.get(identifier.toAvro())).isEqualTo(metadata.toAvro())
        assertThat(map.get(otherIdentifier.toAvro())).isEqualTo(otherMetadata.toAvro())
    }

    /**
     * This scenario should NEVER occur in production, and should be caught in development.
     */
    @Test
    fun `putting mismatched CPI Identifiers throws`() {
        val identifier = CPI.Identifier.newInstance("ghi", "hjk", secureHash)
        val differentIdentifier = CPI.Identifier.newInstance("abc", "def", secureHash)
        val metadata = CPI.Metadata.newInstance(differentIdentifier, secureHash, emptyList(), "")
        assertThrows<IllegalArgumentException> {
            map.put(identifier.toAvro(), metadata.toAvro())
        }
    }

    @Test
    fun `put one and remove one CpiInfo`() {
        val identifier = CPI.Identifier.newInstance("ghi", "hjk", secureHash)
        val metadata = CPI.Metadata.newInstance(identifier, secureHash, emptyList(), "")
        map.put(identifier.toAvro(), metadata.toAvro())

        assertThat(map.get(identifier.toAvro())).isEqualTo(metadata.toAvro())

        val actualCpiInfo = map.remove(identifier.toAvro())
        assertThat(actualCpiInfo).isEqualTo(metadata.toAvro())

        val cpiInfoAgain = map.remove(identifier.toAvro())
        assertThat(cpiInfoAgain).isNull()
    }

    @Test
    fun `test returning map as corda types`() {
        val keys = mutableListOf<CPIIdentifier>()
        val count = 100

        // Add a number of CpiInfo objects to the map, and keep a copy of the keys
        for (i in 0..count) {
            val identifier = CPI.Identifier.newInstance(UUID.randomUUID().toString(), "hjk", secureHash)
            val metadata = CPI.Metadata.newInstance(identifier, secureHash, emptyList(), "")

            val key = identifier.toAvro()
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

        allCpiInfos.forEach { (k, v) ->
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
