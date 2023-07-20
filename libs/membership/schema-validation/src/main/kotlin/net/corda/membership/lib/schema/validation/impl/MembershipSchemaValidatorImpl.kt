package net.corda.membership.lib.schema.validation.impl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.uri.URIFetcher
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PROTOCOL_VERSIONS
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.schema.validation.MembershipSchemaFetchException
import net.corda.membership.lib.schema.validation.MembershipSchemaValidationException
import net.corda.membership.lib.schema.validation.MembershipSchemaValidator
import net.corda.schema.membership.MembershipSchema
import net.corda.schema.membership.provider.MembershipSchemaException
import net.corda.schema.membership.provider.MembershipSchemaProvider
import net.corda.v5.base.versioning.Version
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI

class MembershipSchemaValidatorImpl(
    private val membershipSchemaProvider: MembershipSchemaProvider
) : MembershipSchemaValidator {
    private companion object {
        val REGISTERED_SCHEMES = listOf("https", "http")
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val notaryProtocolVersionRegex = NOTARY_SERVICE_PROTOCOL_VERSIONS.format("[0-9]+").toRegex()
        const val VALIDATION_ERROR = "Exception when validating membership schema."
    }

    private val schemaFactory = buildSchemaFactory()
    private val objectMapper = ObjectMapper()

    override fun validateGroupPolicy(
        schema: MembershipSchema.GroupPolicySchema,
        version: Version,
        groupPolicy: String
    ) {

        val schemaInput = try {
            membershipSchemaProvider.getSchema(schema, version)
        } catch (ex: MembershipSchemaException) {
            val errReason = "Failed to retrieve the schema require to validate the group policy file."
            logger.error(errReason, ex)
            throw MembershipSchemaValidationException(VALIDATION_ERROR, ex, schema, listOf(errReason))
        }
        val groupPolicyJson = try {
            objectMapper.readTree(groupPolicy)
        } catch (ex: Exception) {
            val errReason = "Failed to parse group policy as valid JSON."
            logger.error(errReason, ex)
            throw MembershipSchemaValidationException(VALIDATION_ERROR, ex, schema, listOf(errReason))
        }
        validateJson(schema, schemaInput, groupPolicyJson)
    }

    override fun validateRegistrationContext(
        schema: MembershipSchema.RegistrationContextSchema,
        version: Version,
        registrationContext: Map<String, String>
    ) {
        val schemaInput = try {
            membershipSchemaProvider.getSchema(schema, version)
        } catch (ex: MembershipSchemaException) {
            val errReason = "Failed to retrieve the schema require to validate the registration context."
            logger.error(errReason, ex)
            throw MembershipSchemaValidationException(VALIDATION_ERROR, ex, schema, listOf(errReason))
        }
        val serial = registrationContext[SERIAL]?.toLong()
        val convertedRegistrationContext = mutableMapOf<String, Any>()
        convertedRegistrationContext.putAll(
            registrationContext.filterNot { it.key == SERIAL || notaryProtocolVersionRegex.matches(it.key) }
        )
        if (serial != null) {
            convertedRegistrationContext[SERIAL] = serial
        }
        convertedRegistrationContext.putAll(
            registrationContext.filter {
                notaryProtocolVersionRegex.matches(it.key)
            }.entries.associate { it.key to it.value.toInt() }
        )
        val contextAsJson = try {
            objectMapper.readTree(
                objectMapper.writeValueAsString(
                    convertedRegistrationContext
                )
            )
        } catch (ex: Exception) {
            val errReason = "Failed to map registration context to JSON."
            logger.error(errReason, ex)
            throw MembershipSchemaValidationException(VALIDATION_ERROR, ex, schema, listOf(errReason))
        }
        validateJson(schema, schemaInput, contextAsJson)
    }

    private fun validateJson(
        schemaType: MembershipSchema,
        schemaInput: InputStream,
        json: JsonNode
    ) {
        val jsonSchema = schemaFactory.getSchema(schemaInput)
        val errors = jsonSchema.walk(json, true)
        if (errors.validationMessages.isNotEmpty()) {
            throw MembershipSchemaValidationException(
                VALIDATION_ERROR,
                null,
                schemaType,
                errors.validationMessages.map { it.message }
            )
        }
    }

    private fun buildSchemaFactory(): JsonSchemaFactory {
        val builder = JsonSchemaFactory.builder(
            JsonSchemaFactory.getInstance(
                SpecVersion.VersionFlag.V201909
            )
        )
        builder.uriFetcher(MembershipSchemaURIFetcher(membershipSchemaProvider), REGISTERED_SCHEMES)
        return builder.build()
    }

    private class MembershipSchemaURIFetcher(
        private val membershipSchemaProvider: MembershipSchemaProvider
    ) : URIFetcher {
        private companion object {
            private const val CORDA_SCHEMA_URL = "https://corda.r3.com/"
        }

        override fun fetch(uri: URI?): InputStream {
            if (uri == null) {
                throw MembershipSchemaFetchException("Cannot fetch membership schema for null URI.")
            }
            val resource = uri.toString().removePrefix(CORDA_SCHEMA_URL)
            return try {
                membershipSchemaProvider.getSchemaFile(resource)
            } catch (e: MembershipSchemaException) {
                throw MembershipSchemaFetchException("Could not fetch schema at resource $resource", e)
            }
        }
    }
}