package net.corda.libs.json.validator.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import net.corda.libs.json.validator.WrappedJsonSchema
import net.corda.libs.json.validator.JsonValidator
import org.erdtman.jcs.JsonCanonicalizer
import java.io.InputStream

class JsonValidatorImpl : JsonValidator {
    override fun validate(json: String, wrappedSchema: WrappedJsonSchema) {
        val errors = validateSchema(json, wrappedSchema)

        check(errors.isEmpty()) { "JSON validation failed due to: ${errors.joinToString(",") { it.message }}" }
        check(isCanonical(json)) { "Expected to receive canonical JSON but got:\n  $json" }
    }

    override fun canonicalize(json: String): String = JsonCanonicalizer(json).encodedString

    override fun parseSchema(schema: InputStream): WrappedJsonSchema =
        WrappedJsonSchema(
            JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V201909)
            .getSchema(schema))

    private fun validateSchema(json: String, schemaWrapper: WrappedJsonSchema): Set<ValidationMessage> {
        val data = ObjectMapper().readTree(json)

        return schemaWrapper.schema
            .validate(data)
    }
    private fun isCanonical(json: String): Boolean = canonicalize(json) == json
}