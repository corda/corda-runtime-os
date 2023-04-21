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
        private val expectedItemCounts = listOf(99, 1, 2, 3, 937, 3_754)
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
        val itemCount = 3_749
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

        // check for known false positives until 185
        val falsePositives = setOf(40, 45, 57, 83, 109, 111, 115, 135, 151, 155, 160, 185)
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
    fun `creating bloom filter throws exception if false positive rate is not strictly between zero and one`() {
        val expectedItemCount = 10
        val falsePositiveRates = setOf(-1.0, 0.0, 1.0, 2.0)

        falsePositiveRates.forEach { rate ->
            assertThrows<IllegalArgumentException> {
                createBloomFilter(
                    expectedItemCount, rate, 99
                )
            }
        }
    }

    @Test
    fun `searching for an element in the bloom filter returns false when filter length is zero`() {
        val expectedItemCount = 0
        val falsePositiveRate = 0.5

        val testElementPool = setOf(41, 149, 151, 243, 249, 253, 261, 265, 271, 287)

        createBloomFilter(
            expectedItemCount, falsePositiveRate, 99
        ).apply {
            assertEquals(0, this.filterLength)
            testElementPool.forEach { testElement ->
                assertFalse(this.possiblyContains(testElement.toByteArray()))
            }
        }
    }

    @Test
    fun `searching for an element in the bloom filter returns false when number of hashes is zero`() {
        val expectedItemCount = 0
        val falsePositiveRate = 0.5

        val testElementPool = setOf(41, 149, 151, 243, 249, 253, 261, 265, 271, 287)

        createBloomFilter(
            expectedItemCount, falsePositiveRate, 99
        ).apply {
            assertEquals(0, this.numberOfHashFunctions)
            testElementPool.forEach { testElement ->
                assertFalse(this.possiblyContains(testElement.toByteArray()))
            }
        }
    }
}
