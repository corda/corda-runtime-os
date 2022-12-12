package net.corda.crypto.hes

/**
 * Contains parameters used for Hybrid Encryption Scheme.
 * @property salt used by the HKDF function and provides source of additional entropy, according to that spec
 * https://datatracker.ietf.org/doc/html/rfc5869#section-3.1 it can be exchanged between
 * communicating parties.
 * @property aad the optional additional authentication data used by the GCM.
 */
class HybridEncryptionParams(
    val salt: ByteArray,
    val aad: ByteArray?
)