package net.corda.common.json.validation

import java.io.InputStream

interface JsonValidator {
    fun validate(json: String, schema: InputStream)
    fun canonicalize(json: String): String
}
