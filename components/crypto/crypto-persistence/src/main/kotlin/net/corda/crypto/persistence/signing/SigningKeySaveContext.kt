package net.corda.crypto.persistence.signing

import net.corda.v5.cipher.suite.schemes.KeyScheme

interface SigningKeySaveContext {
    val alias: String?
    val category: String
    val associationId: String
    val keyScheme: KeyScheme
    val externalId: String?
}