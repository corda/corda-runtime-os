package net.corda.libs.packaging.versioning

import net.corda.libs.packaging.VersionComparator
import net.corda.libs.packaging.VersionComparator.Companion.toVersionParts
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class VersionPartsTests {
    @Test
    fun `parse version only`() {
        val parts = "123".toVersionParts()
        Assertions.assertEquals(VersionComparator.DEFAULT_EPOCH, parts.epoch)
        Assertions.assertEquals("123", parts.version)
        Assertions.assertEquals("", parts.release)
    }

    @Test
    fun `parse epoch only`() {
        val parts = "123:".toVersionParts()
        Assertions.assertEquals("123", parts.epoch)
        Assertions.assertEquals("", parts.version)
        Assertions.assertEquals("", parts.release)
    }

    @Test
    fun `parse release only`() {
        val parts = "-123".toVersionParts()
        Assertions.assertEquals(VersionComparator.DEFAULT_EPOCH, parts.epoch)
        Assertions.assertEquals("", parts.version)
        Assertions.assertEquals("123", parts.release)
    }

    @Test
    fun `parse full version string`() {
        val parts = "123:1.0-abc".toVersionParts()
        Assertions.assertEquals("123", parts.epoch)
        Assertions.assertEquals("1.0", parts.version)
        Assertions.assertEquals("abc", parts.release)
    }

    @Test
    fun `parse version string with epoch and release but no version`() {
        val parts = "123:-abc".toVersionParts()
        Assertions.assertEquals("123", parts.epoch)
        Assertions.assertEquals("", parts.version)
        Assertions.assertEquals("abc", parts.release)
    }

    @Test
    fun `parse version string with version and release but no epoch`() {
        val parts = "123-abc".toVersionParts()
        Assertions.assertEquals(VersionComparator.DEFAULT_EPOCH, parts.epoch)
        Assertions.assertEquals("123", parts.version)
        Assertions.assertEquals("abc", parts.release)
    }

    @Test
    fun `parse version string with epoch and version but no release`() {
        val parts = "123:abc".toVersionParts()
        Assertions.assertEquals("123", parts.epoch)
        Assertions.assertEquals("abc", parts.version)
        Assertions.assertEquals("", parts.release)
    }

    @Test
    fun `parse version string with alphabetic version and release`() {
        val parts = "123:abc-def".toVersionParts()
        Assertions.assertEquals("123", parts.epoch)
        Assertions.assertEquals("abc", parts.version)
        Assertions.assertEquals("def", parts.release)
    }

    @Test
    fun `parse version string where alphabetic epoch is actually part of the version`() {
        val parts = "abc:def-ghi".toVersionParts()
        Assertions.assertEquals(VersionComparator.DEFAULT_EPOCH, parts.epoch)
        Assertions.assertEquals("abc:def", parts.version)
        Assertions.assertEquals("ghi", parts.release)
    }

    @Test
    fun `parse odd version that is not an epoch`() {
        val parts = "0:".toVersionParts()
        Assertions.assertEquals(VersionComparator.DEFAULT_EPOCH, parts.epoch)
        Assertions.assertEquals("", parts.version)
        Assertions.assertEquals("", parts.release)
    }

    @Test
    fun `parse odd release with no version`() {
        val parts = "-0".toVersionParts()
        Assertions.assertEquals(VersionComparator.DEFAULT_EPOCH, parts.epoch)
        Assertions.assertEquals("", parts.version)
        Assertions.assertEquals("0", parts.release)
    }


}
