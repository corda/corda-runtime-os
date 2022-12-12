package net.corda.simulator.runtime.signing

import net.corda.v5.application.crypto.SigningService

fun interface SigningServiceFactory {
    fun createSigningService(keyStore: SimKeyStore): SigningService
}

fun signingServiceFactoryBase() = SigningServiceFactory {
        keystore -> SimWithJsonSigningService(keystore)
}