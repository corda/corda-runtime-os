package net.corda.crypto.persistence.signing

import net.corda.v5.cipher.suite.schemes.SignatureScheme

interface SigningKeySaveContext {
    val alias: String?
    val category: String
    val associationId: String
    val signatureScheme: SignatureScheme
    val externalId: String?
}