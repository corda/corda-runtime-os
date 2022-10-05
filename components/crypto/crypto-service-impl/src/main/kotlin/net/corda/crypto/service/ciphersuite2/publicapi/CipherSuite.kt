package net.corda.crypto.service.ciphersuite2.publicapi

import net.corda.crypto.service.ciphersuite2.publicapi.providers.GenerateKeyHandlerProvider
import net.corda.crypto.service.ciphersuite2.publicapi.providers.SignDataHandler
import net.corda.crypto.service.ciphersuite2.publicapi.providers.VerifySignatureHandler

interface CipherSuite {
    val metadata: CipherSchemeMetadata2
    fun register(handler: VerifySignatureHandler)
}

interface CryptoWorkerCipherSuite : CipherSuite {
    fun register(handler: GenerateKeyHandlerProvider)
    fun register(handler: SignDataHandler)
}