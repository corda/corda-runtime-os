package net.corda.crypto.persistence

import net.corda.v5.cipher.suite.schemes.KeyScheme

interface SigningKeySaveContext {
    val alias: String?
    val category: String
    val hsmId: String
    val keyScheme: KeyScheme
    val externalId: String?
}