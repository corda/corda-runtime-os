package net.corda.common.json.validation.impl

import net.corda.v5.base.exceptions.CordaRuntimeException
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import net.corda.common.json.validation.JsonValidator
import org.erdtman.jcs.JsonCanonicalizer
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope
import java.io.InputStream

@Component(service = [JsonValidator::class], scope = ServiceScope.PROTOTYPE)
class JsonValidatorImpl: JsonValidator {
    private val mapper = ObjectMapper()

    override fun validate(json: String, schema: InputStream) {
        val errors = validateSchema(json, schema)

        check(errors.isEmpty()) { "JSON validation failed due to: ${errors.joinToString(",") { it.message }}" }
        check(isCanonical(json)) { "Expected to receive canonical JSON but got:\n  $json" }
    }

    override fun canonicalize(json: String): String = JsonCanonicalizer(json).encodedString

    private fun validateSchema(json: String, schema: InputStream): Set<ValidationMessage> {
        val data = mapper.readTree(json)

        return JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V7)
            .getSchema(schema)
            .validate(data)
    }
    private fun isCanonical(json: String): Boolean = canonicalize(json) == json
}
