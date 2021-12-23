package net.corda.datasync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource


class BloomFilterTest {
    companion object {
        private val expectedItemCounts = listOf(99, 1, 2, 3, 10000, 100000)
        private const val FALSE_POSITIVE_RATE = 0.02
        private val hashSeeds = listOf(99, 1, 2, 3, 9999, 99999)
        private val testElement = 100000.toByteArray()

        @JvmStatic
        fun parameters(): List<BloomFilterTestParameters> {
            val result = mutableListOf<BloomFilterTestParameters>()
            for (i in expectedItemCounts.indices) {
                result.add(
                    BloomFilterTestParameters(
                        expectedItemCounts[i],
                        FALSE_POSITIVE_RATE,
                        hashSeeds[i]
                    )
                )
            }
            return result
        }

        private fun Int.toByteArray(): ByteArray {
            return byteArrayOf((this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), this.toByte())
        }
    }

    data class BloomFilterTestParameters(
        val expectedItemCount: Int,
        val falsePositiveRate: Double,
        val hashSeed: Int
    )

    @ParameterizedTest
    @MethodSource("parameters")
    fun `adding elements into Bloom filter and check if they are added`(bf: BloomFilterTestParameters) {
        val bloomFilter = createBloomFilter(
            bf.expectedItemCount,
            bf.falsePositiveRate,
            bf.hashSeed
        )

        for(i in 0 until bf.expectedItemCount) {
            bloomFilter.add(i.toByteArray())
        }

        for(i in 0 until bf.expectedItemCount) {
            assertEquals(true, bloomFilter.possiblyContains(i.toByteArray()))
        }

        assertFalse(bloomFilter.possiblyContains(testElement))
        bloomFilter.add(testElement)
        assertTrue(bloomFilter.possiblyContains(testElement))
    }

    @Test
    fun `calculating the length of the filter and number of hash functions`() {
        val expectedItemCount = 10
        val falsePositiveRate = 0.5

        val bloomFilter = createBloomFilter(
            expectedItemCount, falsePositiveRate, 99
        )

        assertEquals(15, bloomFilter.filterLength)

        assertEquals(1, bloomFilter.numberOfHashFunctions)
    }

    @Test
    fun `false positive checks`() {
        val itemCount = 100000
        val falsePositiveRate = 0.03
        val bloomFilter = createBloomFilter(
            itemCount,
            falsePositiveRate,
            0
        )

        var i = 0
        while (i < itemCount) {
            bloomFilter.add(i.toByteArray())
            i += 2
        }

        var j = 1
        var count = 0
        while (j < itemCount) {
            if(bloomFilter.possiblyContains(j.toByteArray())) {
                ++count
            }
            j += 2
        }
        assertTrue(count.toDouble() < 2.0 * itemCount * falsePositiveRate)

        // check for known false positives until 287
        val falsePositives = setOf(41, 149, 151, 243, 249, 253, 261, 265, 271, 287)
        falsePositives.forEach {
            assertTrue(bloomFilter.possiblyContains(it.toByteArray()))
        }
        for(m in 0 until falsePositives.last()) {
            if(m % 2 != 0 && !falsePositives.contains(m)) {
                assertFalse(bloomFilter.possiblyContains(m.toByteArray()))
            }
        }
    }

    @Test
    fun `searching for an element in the bloom filter throws exception if filter length is zero`() {
        val expectedItemCount = 10
        val falsePositiveRate = 0.99999999999999999999

        val bloomFilter = createBloomFilter(
            expectedItemCount, falsePositiveRate, 99
        )

        assertThrows<IllegalArgumentException> {
            bloomFilter.possiblyContains(testElement)
        }
    }

    @Test
    fun `searching for an element in the bloom filter throws exception if number of hashes is zero`() {
        val expectedItemCount = 0
        val falsePositiveRate = 0.5

        val bloomFilter = createBloomFilter(
            expectedItemCount, falsePositiveRate, 99
        )

        assertThrows<IllegalArgumentException> {
            bloomFilter.possiblyContains(testElement)
        }
    }
}