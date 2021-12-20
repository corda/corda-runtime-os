package net.corda.libs.configuration.write

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.naming.ConfigurationException

class ConfigVersionNumberTest {

    @Suppress("Deprecation")
    @Test
    fun testCorrectVersionNumbers() {
        val version1 = "4.5"
        val version2 = "4.5-SNAPSHOT"
        Assertions.assertEquals(ConfigVersionNumber(4, 5), ConfigVersionNumber.from(version1))
        Assertions.assertEquals(ConfigVersionNumber(4, 5), ConfigVersionNumber.from(version2))
    }

    @Suppress("Deprecation")
    @Test
    fun testIncorrectVersionNumbers() {
        val version1 = "4.5.4.5"
        val version2 = "4.5-2134354642"

        assertThrows<ConfigurationException> { ConfigVersionNumber.from(version1) }
        assertThrows<ConfigurationException> { ConfigVersionNumber.from(version2) }
    }
}

