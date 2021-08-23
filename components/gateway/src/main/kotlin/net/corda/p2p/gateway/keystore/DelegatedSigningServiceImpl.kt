package net.corda.p2p.gateway.keystore

import net.corda.crypto.SigningService
import net.corda.v5.base.util.contextLogger

/**
 * [DelegatedSigningService] implementation using [SigningService].
 */
class DelegatedSigningServiceImpl(private val signingService: SigningService) : DelegatedSigningService {
    companion object {
        private val log = contextLogger()
    }

    @Suppress("TooGenericExceptionCaught")
    override fun sign(alias: String, data: ByteArray, signAlgorithm: String): ByteArray? {
        try {
            return signingService.sign(alias, data, signAlgorithm)
        } catch (e: Exception) {
            log.error("Error encountered while signing", e)
        }
        return null
    }
}
