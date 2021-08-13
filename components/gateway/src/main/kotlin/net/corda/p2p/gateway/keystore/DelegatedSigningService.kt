package net.corda.p2p.gateway.keystore

/**
 * The DelegatedSigningService is responsible for signing things.
 */
interface DelegatedSigningService {
    fun sign(alias: String, data: ByteArray, signAlgorithm: String): ByteArray?
}
