package net.corda.crypto.ecies

/**
 * Contains parameters used for HKDF
 * @property salt used by the HKDF function and provides source of additional entropy, according to that spec
 * https://datatracker.ietf.org/doc/html/rfc5869#section-3.1 it can be exchanged between
 * communicating parties.
 * @property aad the optional additional authentication data used by the GCM, the provided data will be concatenated
 * with the public keys of the both parties.
 */
class EciesParams(
    val salt: ByteArray,
    val aad: ByteArray?
)