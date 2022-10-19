package net.corda.ledger.common.impl.transaction

interface JsonValidator {
    fun validate(json: String, schemaPath: String)
    fun canonicalize(json: String): String
}
