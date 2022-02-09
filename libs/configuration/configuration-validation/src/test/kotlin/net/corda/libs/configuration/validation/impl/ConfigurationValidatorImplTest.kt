package net.corda.libs.configuration.validation.impl

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.validation.ConfigurationSchemaFetchException
import net.corda.libs.configuration.validation.ConfigurationValidationException
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.schema.configuration.provider.SchemaProvider
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
        private const val INVALID_DATA = "data/invalid.conf"

        private const val TEST_VERSION = "1.0"
    }

    @Test
    fun `valid document against test schema`() {
        val validator = createSchemaValidator()
        val json = loadData(VALID_DATA)
        validator.validate(TEST_SCHEMA, TEST_VERSION, json)
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

    private fun loadData(dataResource: String): SmartConfig {
        val data = loadResource(dataResource).bufferedReader().readText()
        val rawConfig = ConfigFactory.parseString(data)
        return SmartConfigFactory.create(ConfigFactory.empty()).create(rawConfig)
    }

    private fun emptyConfig() : SmartConfig {
        return SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())
    }

    private fun createSchemaValidator(): ConfigurationValidator {
        val schemaProvider = TestSchemaProvider()
        return ConfigurationValidatorImpl(schemaProvider)
    }

    private class TestSchemaProvider : SchemaProvider {
        override fun getSchema(key: String, version: String): InputStream {
            return loadResource(key)
        }

        override fun getSchemaFile(fileName: String): InputStream {
            return loadResource(fileName)
        }
    }
}