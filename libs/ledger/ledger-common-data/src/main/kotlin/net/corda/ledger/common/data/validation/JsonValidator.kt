package net.corda.ledger.common.data.validation

interface JsonValidator {
    fun validate(json: String, schemaPath: String)
    fun canonicalize(json: String): String
}
