package net.corda.chunking.db.impl.validation

import com.typesafe.config.ConfigException
import net.corda.libs.configuration.validation.ConfigurationValidationException
import net.corda.libs.configuration.validation.impl.ConfigurationValidatorFactoryImpl
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkMetadata
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class ExternalChannelsConfigValidatorTest {

    private val externalChannelsConfigValidator =
        ExternalChannelsConfigValidatorImpl(ConfigurationValidatorFactoryImpl().createConfigValidator())

    @Test
    fun `does not throw exception when the configuration is valid - no configuration`() {
        val mockCpkMetadata = mock<CpkMetadata> { on { externalChannelsConfig }.doReturn("{\"channels\": [ ] }") }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata)) }

        assertDoesNotThrow {
            externalChannelsConfigValidator.validate(mockCpiMetadata.cpksMetadata)
        }
    }

    @Test
    fun `does not throw exception when the configuration is valid - one channel`() {
        val mockCpkMetadata = mock<CpkMetadata> {
            on { externalChannelsConfig }.doReturn(
                """
                    {
                      "channels": [
                        {
                            "name": "a.b.c",
                            "type": "send"
                        },
                      ]
                    }
                """.trimIndent()
            )
        }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata)) }

        assertDoesNotThrow {
            externalChannelsConfigValidator.validate(mockCpiMetadata.cpksMetadata)
        }
    }

    @Test
    fun `does not throw exception when the configuration is valid - two channels`() {
        val mockCpkMetadata = mock<CpkMetadata> {
            on { externalChannelsConfig }.doReturn(
                """
                    {
                      "channels": [
                        {
                            "name": "a.b.c",
                            "type": "send"
                        },
                        {
                            "name": "1.2.3",
                            "type": "send-receive"
                        }
                      ]
                    }
                """.trimIndent()
            )
        }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata)) }

        assertDoesNotThrow {
            externalChannelsConfigValidator.validate(mockCpiMetadata.cpksMetadata)
        }
    }

    @Test
    fun `does not throw exception when the configuration is null`() {
        val mockCpkMetadata = mock<CpkMetadata> { on { externalChannelsConfig }.doReturn(null) }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata)) }

        assertDoesNotThrow {
            externalChannelsConfigValidator.validate(mockCpiMetadata.cpksMetadata)
        }
    }

    @Test
    fun `throws exception when the configuration is invalid`() {
        val mockCpkMetadata = mock<CpkMetadata> { on { externalChannelsConfig }.doReturn("invalid schema") }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata)) }

        assertThrows<ConfigException.Parse> {
            externalChannelsConfigValidator.validate(mockCpiMetadata.cpksMetadata)
        }
    }

    @Test
    fun `throws exception when the configuration string is empty`() {
        val mockCpkMetadata = mock<CpkMetadata> { on { externalChannelsConfig }.doReturn("") }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata)) }

        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate(mockCpiMetadata.cpksMetadata)
        }
    }

    @Test
    fun `throws exception when the configuration string contains an object with no properties`() {
        val mockCpkMetadata = mock<CpkMetadata> { on { externalChannelsConfig }.doReturn("{}") }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata)) }

        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate(mockCpiMetadata.cpksMetadata)
        }
    }

    @Test
    fun `throws exception when unknown property is added to the schema`() {
        val mockCpkMetadata = mock<CpkMetadata> {
            on { externalChannelsConfig }.doReturn(
                """
                    {
                      "channels": [
                        {
                            "name": "a.b.c",
                            "type": "send"
                        }
                      ],
                      "unknown": "value"
                    }
                """.trimIndent()
            )
        }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata)) }

        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate(mockCpiMetadata.cpksMetadata)
        }
    }

    @Test
    fun `throws exception when unknown property is added to the schema 2`() {
        val mockCpkMetadata = mock<CpkMetadata> {
            on { externalChannelsConfig }.doReturn(
                """
                    {
                      "channels": [
                        {
                            "name": "a.b.c",
                            "type": "send",
                            "unknown": "value"
                        }
                      ]
                    }
                """.trimIndent()
            )
        }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata)) }

        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate(mockCpiMetadata.cpksMetadata)
        }
    }

    @Test
    fun `throws exception when a property is misnamed - name`() {
        val mockCpkMetadata = mock<CpkMetadata> {
            on { externalChannelsConfig }.doReturn(
                """
                    {
                      "channels": [
                        {
                            "nam": "a.b.c",
                            "type": "send"
                        }
                      ],
                    }
                """.trimIndent()
            )
        }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata)) }

        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate(mockCpiMetadata.cpksMetadata)
        }
    }

    @Test
    fun `throws exception when a property is misnamed - type`() {
        val mockCpkMetadata = mock<CpkMetadata> {
            on { externalChannelsConfig }.doReturn(
                """
                    {
                      "channels": [
                        {
                            "name": "a.b.c",
                            "typ": "send"
                        }
                      ],
                    }
                """.trimIndent()
            )
        }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata)) }

        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate(mockCpiMetadata.cpksMetadata)
        }
    }

    @Test
    fun `throws exception when a property is missing - name `() {
        val mockCpkMetadata = mock<CpkMetadata> {
            on { externalChannelsConfig }.doReturn(
                """
                    {
                      "channels": [
                        {
                            "type": "send"
                        }
                      ],
                    }
                """.trimIndent()
            )
        }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata)) }

        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate(mockCpiMetadata.cpksMetadata)
        }
    }

    @Test
    fun `throws exception when a property is missing - type `() {
        val mockCpkMetadata = mock<CpkMetadata> {
            on { externalChannelsConfig }.doReturn(
                """
                    {
                      "channels": [
                        {
                             "name": "a.b.c"
                        }
                      ],
                    }
                """.trimIndent()
            )
        }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata)) }

        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate(mockCpiMetadata.cpksMetadata)
        }
    }

    @Test
    fun `throws exception when the property type has an unknown value `() {
        val mockCpkMetadata = mock<CpkMetadata> {
            on { externalChannelsConfig }.doReturn(
                """
                    {
                      "channels": [
                        {
                            "name": "a.b.c"
                            "type": "send1"
                        }
                      ],
                    }
                """.trimIndent()
            )
        }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata)) }

        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate(mockCpiMetadata.cpksMetadata)
        }
    }

    @Test
    fun `throws exception when the property name has illegal characters `() {
        val mockCpkMetadata = mock<CpkMetadata> {
            on { externalChannelsConfig }.doReturn(
                """
                    {
                      "channels": [
                        {
                            "name": "a:b:c"
                            "type": "send"
                        }
                      ],
                    }
                """.trimIndent()
            )
        }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata)) }

        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate(mockCpiMetadata.cpksMetadata)
        }
    }

    @Test
    fun `throws exception when the property name is left empty `() {
        val mockCpkMetadata = mock<CpkMetadata> {
            on { externalChannelsConfig }.doReturn(
                """
                    {
                      "channels": [
                        {
                            "name": ""
                            "type": "send"
                        }
                      ],
                    }
                """.trimIndent()
            )
        }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata)) }

        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate(mockCpiMetadata.cpksMetadata)
        }
    }
}
