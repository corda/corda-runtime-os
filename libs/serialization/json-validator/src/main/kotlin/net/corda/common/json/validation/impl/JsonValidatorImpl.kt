package net.corda.common.json.validation.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import net.corda.common.json.validation.JsonValidator
import net.corda.common.json.validation.WrappedJsonSchema
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.erdtman.jcs.JsonCanonicalizer
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope
import java.io.InputStream

@Component(
    service = [ JsonValidator::class, UsedByFlow::class, UsedByPersistence::class, UsedByVerification::class ],
    scope = ServiceScope.PROTOTYPE
)
class JsonValidatorImpl: JsonValidator,
    UsedByFlow, UsedByPersistence, UsedByVerification, SingletonSerializeAsToken {

    override fun validate(json: String, wrappedSchema: WrappedJsonSchema) {
        val errors = validateSchema(json, wrappedSchema)

        check(errors.isEmpty()) { "JSON validation failed due to: ${errors.joinToString(",") { it.message }}" }
        check(isCanonical(json)) { "Expected to receive canonical JSON but got:\n  $json" }
    }

    override fun canonicalize(json: String): String = JsonCanonicalizer(json).encodedString

    override fun parseSchema(schema: InputStream): WrappedJsonSchema =
        // before parsing it, we can check the schema version
        WrappedJsonSchema(JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V201909)
            .getSchema(schema))

    private fun validateSchema(json: String, schemaWrapper: WrappedJsonSchema): Set<ValidationMessage> {
        val data = ObjectMapper().readTree(json)

        return schemaWrapper.schema
            .validate(data)
    }
    private fun isCanonical(json: String): Boolean = canonicalize(json) == json
}
