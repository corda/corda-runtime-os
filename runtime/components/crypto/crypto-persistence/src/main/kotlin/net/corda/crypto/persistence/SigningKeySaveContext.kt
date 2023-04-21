package net.corda.crypto.persistence

import net.corda.crypto.cipher.suite.schemes.KeyScheme

interface SigningKeySaveContext {
    val alias: String?
    val category: String
    val hsmId: String
    val keyScheme: KeyScheme
    val externalId: String?
}