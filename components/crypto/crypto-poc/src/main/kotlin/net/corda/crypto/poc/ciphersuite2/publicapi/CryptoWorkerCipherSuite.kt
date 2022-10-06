package net.corda.crypto.poc.ciphersuite2.publicapi

import net.corda.crypto.poc.ciphersuite2.publicapi.providers.GenerateKeyHandlerProvider
import net.corda.crypto.poc.ciphersuite2.publicapi.providers.KeyEncodingHandler
import net.corda.crypto.poc.ciphersuite2.publicapi.providers.SignDataHandler
import net.corda.crypto.poc.ciphersuite2.publicapi.providers.VerifySignatureHandler
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.SignatureSpec

interface CryptoWorkerCipherSuite {
    fun register(
        keyScheme: KeyScheme,
        signatureSpecs: List<SignatureSpec>,
        encodingHandler: KeyEncodingHandler,
        verifyHandler: VerifySignatureHandler?,
        generateHandler: GenerateKeyHandlerProvider?,
        signHandler: SignDataHandler?
    )
}