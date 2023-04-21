package net.corda.libs.packaging.versioning

import net.corda.libs.packaging.core.comparator.VersionComparator
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.random.Random

// Remove this annotation to run 100,000 fuzz tests to see if anything is broken.
@Disabled
class VersionComparatorFuzzerTests {
    companion object {
        private val randomPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf(':', '-', '!')
        private val numberPool: List<Char> = ('0'..'9') + emptyList()
        private val alphaPool: List<Char> = ('a'..'z') + ('A'..'Z')
        const val TOTAL = 100000

        fun randomString(length: Int): String = randomChars(length, randomPool)

        private fun randomNumber(length: Int): String = randomChars(length, numberPool)

        private fun randomAlpha(): String = randomChars(Random.nextInt(1, 10), alphaPool)

        private fun randomChars(length: Int, charPool: List<Char>): String = (1..length)
            .map { Random.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("")

        private fun randomVersion(): String =
            (1..Random.nextInt(1, 4))
                .joinToString(".") { randomNumber(Random.nextInt(1, 6)) }

        private fun randomEpochAndVersion(): String =
            listOf(randomAlpha(), randomVersion()).joinToString(":")

        private fun randomVersionAndRelease(): String =
            listOf(randomVersion(), randomAlpha()).joinToString("-")

        private fun randomFullVersion(): String =
            listOf(randomEpochAndVersion(), randomAlpha()).joinToString("-")
    }

    @Suppress("TooGenericExceptionCaught")
    private fun testVersion(v1: String, v2: String) {
        try {
            // we don't know whether the result = -1 or +1 (or even zero)
            val result = VersionComparator.cmp(v1, v2)
            val oppositeResult = VersionComparator.cmp(v2, v1)

            // Check equality
            Assertions.assertEquals(0, VersionComparator.cmp(v1, v1), "when testing with \"$v1\", \"$v2\"")
            Assertions.assertEquals(0, VersionComparator.cmp(v2, v2), "when testing with \"$v1\", \"$v2\"")

            // But we know the opposite result (+1, 0, -1) plus original result (-1, 0, +1) must be zero
            // Ensures comparison is commutative
            Assertions.assertEquals(0, result + oppositeResult, "when testing with \"$v1\", \"$v2\"")
        } catch (e: Exception) {
            // Catch and print out the failing strings for testing
            Assertions.fail("when calling  VersionComparator.ncmp(\"$v1\", \"$v2\")")
        }
    }

    @Test
    fun `fuzz completely random strings`() {
        for (i in 1..20) {
            for (j in 1..TOTAL) {
                val v1 = randomString(i)
                val v2 = randomString(i)
                testVersion(v1, v2)
            }
        }
    }

    @Test
    fun `fuzz version strings only`() {
        for (i in 1..TOTAL) {
            val v1 = randomVersion()
            val v2 = randomVersion()
            testVersion(v1, v2)
        }
    }

    @Test
    fun `fuzz epoch-version strings only`() {
        for (i in 1..TOTAL) {
            val v1 = randomEpochAndVersion()
            val v2 = randomEpochAndVersion()
            testVersion(v1, v2)
        }
    }

    @Test
    fun `fuzz version-release strings only`() {
        for (i in 1..TOTAL) {
            val v1 = randomVersionAndRelease()
            val v2 = randomVersionAndRelease()
            testVersion(v1, v2)
        }
    }

    @Test
    fun `fuzz full version strings only`() {
        for (i in 1..TOTAL) {
            val v1 = randomFullVersion()
            val v2 = randomVersionAndRelease()
            testVersion(v1, v2)
        }
    }
}
