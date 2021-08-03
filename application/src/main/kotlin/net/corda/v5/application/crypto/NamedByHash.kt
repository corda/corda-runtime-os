package net.corda.v5.application.crypto

import net.corda.v5.crypto.SecureHash

/** Implemented by anything that can be named by a secure hash value (e.g. transactions, attachments). */
interface NamedByHash {
    val id: SecureHash
}
