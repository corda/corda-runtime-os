package net.corda.datasync.bloomfilter

import net.corda.data.WireBloomFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.BitSet

class BloomFilterTest {
    @Test
    fun `adding elements into Bloom filter and check if they exists works`() {
        val expectedItemCount = 100
        val falsePositiveRate = 0.02
        val hashSeed = 99
        val filterLength = BloomFilter.filterLengthCalc(expectedItemCount, falsePositiveRate)
        val numberOfHashFunctions = BloomFilter.numberOfHashFunctions(filterLength, expectedItemCount)

        val bloomFilter = WireBloomFilter(
            numberOfHashFunctions,
            hashSeed,
            filterLength,
            ByteBuffer.wrap(BitSet(filterLength).toByteArray())
        )

        for(i in 0 until 100) {
            bloomFilter.add(i.toByteArray())
        }

        val bloomFilterWithDifferentBitSet = WireBloomFilter(
            numberOfHashFunctions,
            hashSeed,
            filterLength,
            ByteBuffer.wrap(BitSet(filterLength).toByteArray())
        )

        for(i in 101 until 200) {
            bloomFilterWithDifferentBitSet.add(i.toByteArray())
        }

        assertNotEquals(bloomFilter, bloomFilterWithDifferentBitSet)

        for(i in 0 until 100) {
            assertEquals(true, bloomFilter.possiblyContains(i.toByteArray()))
        }

        for(i in 101 until 200) {
            assertEquals(true, bloomFilterWithDifferentBitSet.possiblyContains(i.toByteArray()))
        }

        assertFalse(bloomFilter.possiblyContains(1000.toByteArray()))
        assertFalse(bloomFilterWithDifferentBitSet.possiblyContains(1000.toByteArray()))
    }

    @Test
    fun `calculating the length of the filter and number of hash functions works`() {
        val expectedItemCount = 10
        val falsePositiveRate = 0.5

        val filterLength = BloomFilter.filterLengthCalc(expectedItemCount, falsePositiveRate)
        assertEquals(15, filterLength)

        val numberOfHashFunctions = BloomFilter.numberOfHashFunctions(filterLength, expectedItemCount)
        assertEquals(2, numberOfHashFunctions)
    }

    private fun Int.toByteArray(): ByteArray {
        return byteArrayOf((this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), this.toByte())
    }
}