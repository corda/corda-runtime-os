package net.corda.ledger.common.impl.transaction

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.erdtman.jcs.JsonCanonicalizer

class JsonValidatorImpl: JsonValidator {
    private val mapper = ObjectMapper()

    override fun validate(json: String, schemaPath: String) {
        val errors = validateSchema(json, schemaPath)

        check(errors.isEmpty()) { "JSON validation failed due to: ${errors.joinToString(",") { it.message }}" }
        check(isCanonical(json)) { "Expected to receive canonical JSON but got:\n  $json" }
    }

    override fun canonicalize(json: String): String = JsonCanonicalizer(json).encodedString

    private fun validateSchema(json: String, schemaPath: String): Set<ValidationMessage> {
        val data = mapper.readTree(json)
        val schema = JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V7)
            .getSchema(
                javaClass.getResourceAsStream(schemaPath)
                    ?: throw CordaRuntimeException("Couldn't load JSON schema from $schemaPath")
            )
        return schema.validate(data)
    }
    private fun isCanonical(json: String): Boolean = canonicalize(json) == json
}
