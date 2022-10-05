package net.corda.crypto.service.ciphersuite2.publicapi.providers

import net.corda.crypto.service.ciphersuite2.publicapi.CipherSuite
import net.corda.crypto.service.ciphersuite2.publicapi.CryptoWorkerCipherSuite

interface CipherSuiteRegistrar {
    fun registerWith(suite: CipherSuite)
}

interface CryptoWorkerCipherSuiteRegistrar {
    fun registerWith(suite: CryptoWorkerCipherSuite)
}