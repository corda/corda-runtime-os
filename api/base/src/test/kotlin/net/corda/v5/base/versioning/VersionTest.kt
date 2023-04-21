package net.corda.v5.base.versioning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class VersionTest {

    @Test
    fun `parse a version from valid version strings`() {
        val version = Version.fromString("1.0")
        assertEquals(1, version.major)
        assertEquals(0, version.minor)
    }

    @ParameterizedTest(name = "parse a version from invalid version strings: {0}")
    @ValueSource(strings = ["foo", "1.1.3", "abc.cdf", "1", "-1.-3", "1.a", "b.6", "1a.b5", "1."])
    fun `parse a version from invalid version strings`(versionString: String) {
        assertThrows<IllegalArgumentException> {
            Version.fromString(versionString)
        }
    }
}