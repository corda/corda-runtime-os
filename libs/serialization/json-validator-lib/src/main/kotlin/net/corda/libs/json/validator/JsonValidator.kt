package net.corda.libs.json.validator

import java.io.InputStream

interface JsonValidator {
    fun validate(json: String, wrappedSchema: WrappedJsonSchema)
    fun canonicalize(json: String): String
    fun parseSchema(schema: InputStream): WrappedJsonSchema
}
