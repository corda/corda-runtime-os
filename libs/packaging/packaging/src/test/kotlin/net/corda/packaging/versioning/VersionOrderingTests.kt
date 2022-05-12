package net.corda.packaging.versioning

import net.corda.packaging.VersionComparator
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class VersionOrderingTests {
    @Test
    fun `basic ordering`() {
        val versions = listOf("1.4", "1.3", "1.0", "1.1", "1.2")
        val sorted = versions.sortedWith(VersionComparator::cmp)

        Assertions.assertEquals(listOf("1.0", "1.1", "1.2", "1.3", "1.4"), sorted)
    }

    @Test
    fun `ordering with letters`() {
        val versions = listOf("1.foo", "1.3", "1.0", "1.1", "1.2")
        val sorted = versions.sortedWith(VersionComparator::cmp)

        Assertions.assertEquals(listOf("1.foo", "1.0", "1.1", "1.2", "1.3"), sorted)
    }

//     Proper ordering should look like this?
//    @Test
//    fun releaseOrderingTest() {
//        val versions = listOf("1.0", "1.0-20210908", "1.0-SNAPSHOT", "1.0")
//        val sorted = versions.sortedWith(VersionComparator::propercmp)
//        Assertions.assertEquals(listOf("1.0", "1.0", "1.0-SNAPSHOT", "1.0-20210908"), sorted)
//    }
}
