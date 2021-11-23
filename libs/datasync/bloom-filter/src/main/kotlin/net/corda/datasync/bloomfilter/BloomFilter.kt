package net.corda.datasync.bloomfilter

import net.corda.data.WireBloomFilter
import java.nio.ByteBuffer
import java.util.BitSet

/**
 * Adding elements to a filter.
 */
fun WireBloomFilter.add(bytes: ByteArray) {
    val hash1 = Integer.toUnsignedLong(MurmurHash3.hash32(bytes, 0, bytes.size, hashSeed))
    val hash2 = Integer.toUnsignedLong(MurmurHash3.hash32(bytes, 0, bytes.size, hash1.toInt()))
    val bitSet = BitSet.valueOf(filterBits)
    for (i in 0 until numberOfHashFunctions) {
        val roundHash = (hash1 + i.toLong() * hash2)
        val hashPos = roundHash.rem(filterLength)
        bitSet.set(hashPos.toInt())
    }
    filterBits = ByteBuffer.wrap(bitSet.toByteArray())
}

/**
 * Checks if a given element is POSSIBLY in the filter or not.
 * Please note, it's the nature of Bloom filters that they can produce false positives.
 */
fun WireBloomFilter.possiblyContains(bytes: ByteArray): Boolean {
    val hash1 = Integer.toUnsignedLong(MurmurHash3.hash32(bytes, 0, bytes.size, hashSeed))
    val hash2 = Integer.toUnsignedLong(MurmurHash3.hash32(bytes, 0, bytes.size, hash1.toInt()))
    for (i in 0 until numberOfHashFunctions) {
        val roundHash = (hash1 + i.toLong() * hash2)
        val hashPos = roundHash.rem(filterLength)
        if (!BitSet.valueOf(filterBits).get(hashPos.toInt())) {
            return false
        }
    }
    return true
}

object BloomFilter {
    private val LN2: Double = Math.log(2.0)
    private val LN2_SQUARED: Double = LN2 * LN2

    /**
     * Calculates the number of hash functions in a Bloom filter.
     * The length of the filter must be calculated first.
     *
     * @param filterLength The length of the filter.
     * @param expectedItemCount The number of items.
     */
    fun numberOfHashFunctions(filterLength: Int, expectedItemCount: Int): Int =
        Math.ceil((filterLength * LN2) / expectedItemCount).toInt()

    /**
     * Calculates the length of the filter in a Bloom filter.
     *
     * @param expectedItemCount The number of items.
     * @param falsePositiveRate The false positive rate used for calculating the filter.
     */
    fun filterLengthCalc(expectedItemCount: Int, falsePositiveRate: Double): Int =
        Math.ceil((-expectedItemCount.toDouble() * Math.log(falsePositiveRate)) / LN2_SQUARED).toInt()
}