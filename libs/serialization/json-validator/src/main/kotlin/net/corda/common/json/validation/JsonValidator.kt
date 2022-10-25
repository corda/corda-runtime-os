package net.corda.common.json.validation

interface JsonValidator {
    fun validate(json: String, schemaPath: String)
    fun canonicalize(json: String): String
}
