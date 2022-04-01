package net.corda.crypto.persistence

import net.corda.v5.cipher.suite.schemes.SignatureScheme

interface SigningKeySaveContext {
    val alias: String?
    val category: String
    val signatureScheme: SignatureScheme
}