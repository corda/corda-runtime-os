package net.corda.ledger.common.impl.transaction

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import net.corda.v5.base.exceptions.CordaRuntimeException

class JsonValidatorImpl(val schemaPath: String): JsonValidator {
    private val mapper = ObjectMapper()

    override fun validate(json: String) {
        val data = mapper.readTree(json)
        val schema = JsonSchemaFactory
                        .getInstance(SpecVersion.VersionFlag.V7)
                        .getSchema(
                            javaClass.getResourceAsStream(schemaPath)
                                ?: throw CordaRuntimeException("Couldn't load JSON schema from $schemaPath")
                        )
        val errors = schema.validate(data)

        if (errors.isNotEmpty()) {
            throw CordaRuntimeException("Failed to validate JSON due to the following errors: ${
                errors.joinToString(",") { it.message }
            }")
        }
    }
}
