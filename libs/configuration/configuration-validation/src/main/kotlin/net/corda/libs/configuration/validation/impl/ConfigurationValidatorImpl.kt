package net.corda.libs.configuration.validation.impl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.ApplyDefaultsStrategy
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SchemaValidatorsConfig
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import java.io.InputStream
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.configuration.validation.ConfigurationSchemaFetchException
import net.corda.libs.configuration.validation.ConfigurationValidationException
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.schema.configuration.provider.SchemaProvider
import net.corda.v5.base.util.debug
import net.corda.v5.base.versioning.Version
import org.slf4j.LoggerFactory

internal class ConfigurationValidatorImpl(private val schemaProvider: SchemaProvider) : ConfigurationValidator {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val REGISTERED_SCHEMES = listOf("https", "http")
    }

    private val schemaFactory = buildSchemaFactory()
    private val objectMapper = ObjectMapper()
    private val configSecretHelper = ConfigSecretHelper()

    override fun validate(key: String, version: Version, config: SmartConfig, applyDefaults: Boolean) : SmartConfig {
        val schemaInput = try {
            schemaProvider.getSchema(key, version)
        } catch (e: Exception) {
            val message = "Could not retrieve the schema for key $key at schema version $version: ${e.message}"
            logger.error(message, e)
            throw ConfigurationSchemaFetchException(message, e)
        }
        val configAsJSONNode = validateConfigAndGetJSONNode(key, config, schemaInput, version, applyDefaults)
        return config.factory.create(ConfigFactory.parseString(configAsJSONNode.toString()))
    }

    override fun validate(key: String, config: SmartConfig, schemaInput: InputStream, applyDefaults: Boolean) : SmartConfig {
        val configAsJSONNode = validateConfigAndGetJSONNode(key, config, schemaInput, null, applyDefaults)
        return config.factory.create(ConfigFactory.parseString(configAsJSONNode.toString()))
    }

    override fun getDefaults(key: String, version: Version) : Config {
        val schemaInput = try {
            schemaProvider.getSchema(key, version)
        } catch (e: Exception) {
            val message = "Could not retrieve the schema for key $key at schema version $version: ${e.message}"
            logger.error(message, e)
            throw ConfigurationSchemaFetchException(message, e)
        }
        val configAsJSONNode = objectMapper.createObjectNode()
        val errors = try {
            val schema = getSchema(schemaInput, applyDefaults = true)
            schema.walk(configAsJSONNode, true).validationMessages
        } catch (e: Exception) {
            val message = "Could not retrieve schema defaults for key $key at schema version $version: ${e.message}"
            logger.error(message, e)
            throw ConfigurationSchemaFetchException(message, e)
        }
        handleErrors(errors, key, version, SmartConfigImpl.empty())
        return ConfigFactory.parseString(configAsJSONNode.toString())
    }

    private fun validateConfigAndGetJSONNode(
        key: String,
        config: SmartConfig,
        schemaInput: InputStream,
        version: Version?,
        applyDefaults: Boolean
    ): JsonNode {
        logger.info("Configuration to validate: ${config.toSafeConfig().root().render(ConfigRenderOptions.concise())}")
        //jsonNode is updated in place by walker when [applyDefaults] is true
        val configAsJSONNode = config.toJsonNode()
        val secretsNode = configSecretHelper.hideSecrets(configAsJSONNode)
        val errors = try {
            // Note that the JSON schema library does lazy schema loading, so schema retrieval issues may not manifest
            // until the validation stage.
            val schema = getSchema(schemaInput, applyDefaults)
            logger.info("Schema to validate against: $schema")
            schema.walk(configAsJSONNode, true).validationMessages
        } catch (e: Exception) {
            val message = "Could not retrieve the schema for key $key at schema version $version: ${e.message}"
            logger.error(message, e)
            throw ConfigurationSchemaFetchException(message, e)
        }
        handleErrors(errors, key, null, config)
        configSecretHelper.insertSecrets(configAsJSONNode, secretsNode)
        return configAsJSONNode
    }

    private fun handleErrors(
        errors: MutableSet<ValidationMessage>,
        key: String,
        version: Version?,
        config: SmartConfig
    ) {
        if (errors.isNotEmpty()) {
            val errorSet = errors.map { it.message }.toSet()
            logger.error(
                "Configuration validation failed for config key $key. Errors: $errorSet."
            )
            throw ConfigurationValidationException(key, version, config, errorSet).also {
                logger.debug { it.fullErrorDetail() }
            }
        }
    }

    private fun getSchema(schemaInput: InputStream, applyDefaults: Boolean): JsonSchema {
        val schemaValidatorsConfig = SchemaValidatorsConfig().apply {
            applyDefaultsStrategy = ApplyDefaultsStrategy(applyDefaults, false, false)
        }
        return schemaFactory.getSchema(schemaInput, schemaValidatorsConfig)
    }

    private fun SmartConfig.toJsonNode(): JsonNode {
        val jsonString = this.root().render(ConfigRenderOptions.concise())
        return objectMapper.readTree(jsonString)
    }

    private fun buildSchemaFactory(): JsonSchemaFactory {
        val builder = JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7))
        builder.uriFetcher(CordaURIFetcher(schemaProvider), REGISTERED_SCHEMES)
        return builder.build()
    }

}