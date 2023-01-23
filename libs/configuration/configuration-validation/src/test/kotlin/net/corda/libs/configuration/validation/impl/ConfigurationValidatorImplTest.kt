package net.corda.libs.configuration.validation.impl

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.validation.ConfigurationSchemaFetchException
import net.corda.libs.configuration.validation.ConfigurationValidationException
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.schema.configuration.provider.SchemaProvider
import net.corda.v5.base.versioning.Version
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.InputStream

// Note: these tests use a stub SchemaProvider to cheat the validator into treating the key parameter as a resource to
// retrieve. In actual use the validator should be provided one of the top level keys from the config schema module
// instead.
class ConfigurationValidatorImplTest {

    companion object {
        private fun loadResource(resource: String): InputStream {
            val url = this::class.java.classLoader.getResource(resource)
                ?: throw IllegalArgumentException("Failed to find $resource")
            return url.openStream()
        }

        private const val TEST_SCHEMA = "schema/valid/test-schema.json"
        private const val INVALID_SCHEMA = "schema/invalid/bad-schema.json"
        private const val BAD_REFERENCE = "schema/bad-reference/bad-reference.json"
        private const val DRAFT_V6 = "schema/wrong-draft/draft-v6.json"
        private const val NO_FILE_EXISTS = "no-file-here"

        private const val VALID_DATA = "data/valid.conf"
        private const val VALID_MISSING_REFERENCE_DATA = "data/valid-missing-reference.conf"
        private const val INVALID_DATA = "data/invalid.conf"

        private val TEST_VERSION = Version.fromString("1.0")
    }

    @Test
    fun `valid document against test schema`() {
        val validator = createSchemaValidator()
        val smartConfig = loadData(VALID_DATA)
        val outputConfig = validator.validate(TEST_SCHEMA, TEST_VERSION, smartConfig)
        assertThat(smartConfig).isEqualTo(outputConfig)
    }

    @Test
    fun `valid document against test schema, applies defaults`() {
        val validator = createSchemaValidator()
        val smartConfig = loadData(VALID_DATA)
        val outputConfig = validator.validate(TEST_SCHEMA, TEST_VERSION, smartConfig, true)
        assertThat(smartConfig).isNotEqualTo(outputConfig)
        assertThat(outputConfig.getInt("testInteger")).isEqualTo(7)
    }

    @Test
    fun `valid document against test schema with missing testReference fields, applies defaults`() {
        val validator = createSchemaValidator()
        val smartConfig = loadData(VALID_DATA)
        val outputConfig = validator.validate(TEST_SCHEMA, TEST_VERSION, smartConfig, true)
        assertThat(smartConfig).isNotEqualTo(outputConfig)
        assertThat(outputConfig.getBoolean("testReference.bar")).isEqualTo(false)
    }

    @Test
    fun `calling get defaults before validate allows defaults to still be populated`() {
        val validator = createSchemaValidator()
        val smartConfig = loadData(VALID_DATA)
        val defaults = validator.getDefaults(TEST_SCHEMA, TEST_VERSION)
        val outputConfig = validator.validate(TEST_SCHEMA, TEST_VERSION, smartConfig, true)
        assertThat(smartConfig).isNotEqualTo(outputConfig)
        assertThat(outputConfig.getBoolean("testReference.bar")).isEqualTo(false)
        assertThat(outputConfig.getInt("testObject.testPropertyB.b")).isEqualTo(2)
        assertThat(defaults.getInt("testObject.testPropertyA.a")).isEqualTo(1)
    }

    @Test
    fun `invalid document against test schema`() {
        val json = loadData(INVALID_DATA)
        val validator = createSchemaValidator()
        val exception = assertThrows<ConfigurationValidationException> {
            validator.validate(TEST_SCHEMA, TEST_VERSION, json)
        }
        assertEquals(3, exception.errors.size)
        assertEquals(TEST_SCHEMA, exception.key)
    }

    @Test
    fun `throws when schema is malformed`() {
        val validator = createSchemaValidator()
        assertThrows<ConfigurationSchemaFetchException> {
            validator.validate(INVALID_SCHEMA, TEST_VERSION, emptyConfig())
        }
    }

    @Test
    fun `throws when a reference cannot be resolved`() {
        val validator = createSchemaValidator()
        assertThrows<ConfigurationSchemaFetchException> {
            // Validation will only try and resolve references as needed, so we need to provide some data that will
            // trigger reference resolution.
            validator.validate(BAD_REFERENCE, TEST_VERSION, loadData(VALID_DATA))
        }
    }

    @Test
    fun `throws if the wrong schema draft is declared`() {
        val validator = createSchemaValidator()
        assertThrows<ConfigurationSchemaFetchException> {
            validator.validate(DRAFT_V6, TEST_VERSION, emptyConfig())
        }
    }

    @Test
    fun `throws if invalid schema file is requested`() {
        val validator = createSchemaValidator()
        assertThrows<ConfigurationSchemaFetchException> {
            validator.validate(NO_FILE_EXISTS, TEST_VERSION, emptyConfig())
        }
    }

    @Test
    fun `default values from test schema`() {
        val validator = createSchemaValidator()
        val expectedConfig = ConfigFactory.parseString("""
            testInteger=7
            testReference.bar=false
            testReference.foo=[1,2,3]
            testObject.testPropertyA.a=1
        """.trimIndent())

        val outputConfig = validator.getDefaults(TEST_SCHEMA, TEST_VERSION)

        assertThat(outputConfig).isEqualTo(expectedConfig)
    }

    @Test
    fun `throws when retrieving default values from malformed schema`() {
        val validator = createSchemaValidator()
        assertThrows<ConfigurationSchemaFetchException> {
            validator.getDefaults(INVALID_SCHEMA, TEST_VERSION)
        }
    }

    private fun loadData(dataResource: String): SmartConfig {
        val data = loadResource(dataResource).bufferedReader().readText()
        val rawConfig = ConfigFactory.parseString(data)
        return SmartConfigFactory.createWithoutSecurityServices().create(rawConfig)
    }

    private fun emptyConfig() : SmartConfig {
        return SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.empty())
    }

    private fun createSchemaValidator(): ConfigurationValidator {
        val schemaProvider = TestSchemaProvider()
        return ConfigurationValidatorImpl(schemaProvider)
    }

    private class TestSchemaProvider : SchemaProvider {
        override fun getSchema(key: String, version: Version): InputStream {
            return loadResource(key)
        }

        override fun getSchemaFile(fileName: String): InputStream {
            return loadResource(fileName)
        }
    }
}