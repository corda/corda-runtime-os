package net.corda.libs.configuration.publish

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.naming.ConfigurationException

class ConfigVersionNumberTest {

    @Test
    fun testCorrectVersionNumbers() {
        val version1 = "4.5"
        val version2 = "4.5-SNAPSHOT"
        Assertions.assertEquals(ConfigVersionNumber(4, 5), ConfigVersionNumber.from(version1))
        Assertions.assertEquals(ConfigVersionNumber(4, 5), ConfigVersionNumber.from(version2))
    }

    @Test
    fun testIncorrectVersionNumbers() {
        val version1 = "4.5.4.5"
        val version2 = "4.5-2134354642"

        assertThrows<ConfigurationException> { ConfigVersionNumber.from(version1) }
        assertThrows<ConfigurationException> { ConfigVersionNumber.from(version2) }
    }
}

