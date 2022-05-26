package net.corda.crypto.tck.testing.hsms

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.KeyScheme

fun produceSupportedSchemes(schemeMetadata: CipherSchemeMetadata, codes: List<String>): List<KeyScheme> =
    mutableListOf<KeyScheme>().apply {
        codes.forEach {
            addIfSupported(schemeMetadata, it)
        }
    }

private fun MutableList<KeyScheme>.addIfSupported(
    schemeMetadata: CipherSchemeMetadata,
    codeName: String
) {
    if (schemeMetadata.schemes.any { it.codeName.equals(codeName, true) }) {
        add(schemeMetadata.findKeyScheme(codeName))
    }
}