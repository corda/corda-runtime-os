package net.corda.libs.configuration.validation.impl

import com.typesafe.config.ConfigException
import net.corda.libs.configuration.validation.ConfigurationValidationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class ExternalChannelsConfigValidatorImplTest {

    private val externalChannelsConfigValidator =
        ConfigurationValidatorFactoryImpl().createExternalChannelsConfigValidator()

    @Test
    fun `does not throw exception when the configuration is valid - no configuration`() {
        assertDoesNotThrow {
            externalChannelsConfigValidator.validate("{\"channels\": [ ] }")
        }
    }

    @Test
    fun `does not throw exception when the configuration is valid - one channel`() {
        val externalChannelsConfig =
            """
                {
                  "channels": [
                    {
                        "name": "a.b.c",
                        "type": "SEND"
                    },
                  ]
                }
            """.trimIndent()

        assertDoesNotThrow {
            externalChannelsConfigValidator.validate(externalChannelsConfig)
        }
    }

    @Test
    fun `does not throw exception when the configuration is valid - two channels`() {
        val externalChannelsConfig =
            """
                {
                  "channels": [
                    {
                        "name": "a.b.c",
                        "type": "SEND"
                    },
                    {
                        "name": "1.2.3",
                        "type": "SEND_RECEIVE"
                    }
                  ]
                }
            """.trimIndent()

        assertDoesNotThrow {
            externalChannelsConfigValidator.validate(externalChannelsConfig)
        }
    }

    @Test
    fun `throws exception when the configuration is invalid`() {
        assertThrows<ConfigException.Parse> {
            externalChannelsConfigValidator.validate("invalid schema")
        }
    }

    @Test
    fun `throws exception when the configuration string is empty`() {
        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate("")
        }
    }

    @Test
    fun `throws exception when the configuration string contains an object with no properties`() {
        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate("{}")
        }
    }

    @Test
    fun `throws exception when unknown property is added to the schema`() {
        val externalChannelsConfig =
            """
                {
                  "channels": [
                    {
                        "name": "a.b.c",
                        "type": "SEND"
                    }
                  ],
                  "unknown": "value"
                }
            """.trimIndent()

        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate(externalChannelsConfig)
        }
    }

    @Test
    fun `throws exception when unknown property is added to the schema 2`() {
        val externalChannelsConfig =
            """
                {
                  "channels": [
                    {
                        "name": "a.b.c",
                        "type": "SEND",
                        "unknown": "value"
                    }
                  ]
                }
            """.trimIndent()

        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate(externalChannelsConfig)
        }
    }

    @Test
    fun `throws exception when a property is misnamed - name`() {
        val externalChannelsConfig =
            """
                {
                  "channels": [
                    {
                        "nam": "a.b.c",
                        "type": "SEND"
                    }
                  ],
                }
            """.trimIndent()

        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate(externalChannelsConfig)
        }
    }

    @Test
    fun `throws exception when a property is misnamed - type`() {
        val externalChannelsConfig =
            """
                {
                  "channels": [
                    {
                        "name": "a.b.c",
                        "typ": "SEND"
                    }
                  ],
                }
            """.trimIndent()

        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate(externalChannelsConfig)
        }
    }

    @Test
    fun `throws exception when a property is missing - name `() {
        val externalChannelsConfig =
            """
                {
                  "channels": [
                    {
                        "type": "SEND"
                    }
                  ],
                }
            """.trimIndent()

        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate(externalChannelsConfig)
        }
    }

    @Test
    fun `throws exception when a property is missing - type `() {
        val externalChannelsConfig =
            """
                {
                  "channels": [
                    {
                         "name": "a.b.c"
                    }
                  ],
                }
            """.trimIndent()

        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate(externalChannelsConfig)
        }
    }

    @Test
    fun `throws exception when the property type has an unknown value `() {
        val externalChannelsConfig =
            """
                {
                  "channels": [
                    {
                        "name": "a.b.c"
                        "type": "SEND1"
                    }
                  ],
                }
            """.trimIndent()

        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate(externalChannelsConfig)
        }
    }

    @Test
    fun `throws exception when the property name has illegal characters `() {
        val externalChannelsConfig =
            """
                {
                  "channels": [
                    {
                        "name": "a:b:c"
                        "type": "SEND"
                    }
                  ],
                }
            """.trimIndent()

        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate(externalChannelsConfig)
        }
    }

    @Test
    fun `throws exception when the property name is left empty `() {
        val externalChannelsConfig =
            """
                {
                  "channels": [
                    {
                        "name": ""
                        "type": "SEND"
                    }
                  ],
                }
            """.trimIndent()

        assertThrows<ConfigurationValidationException> {
            externalChannelsConfigValidator.validate(externalChannelsConfig)
        }
    }
}
