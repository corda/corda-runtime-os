package net.corda.crypto.ecies

/**
 * Contains parameters used for ECIES.
 * @property salt used by the HKDF function and provides source of additional entropy, according to that spec
 * https://datatracker.ietf.org/doc/html/rfc5869#section-3.1 it can be exchanged between
 * communicating parties.
 * @property aad the optional additional authentication data used by the GCM.
 */
class EciesParams(
    val salt: ByteArray,
    val aad: ByteArray?
)