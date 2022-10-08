package net.corda.crypto.persistence

import net.corda.v5.cipher.suite.KeyScheme

interface SigningKeySaveContext {
    val alias: String?
    val keyScheme: KeyScheme
    val externalId: String?
}