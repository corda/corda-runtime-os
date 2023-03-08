package net.corda.crypto.softhsm.impl

import net.corda.crypto.softhsm.KeyPairGeneratorFactory
import java.security.KeyPairGenerator
import java.security.Provider

class JavaKeyPairGenerator : KeyPairGeneratorFactory {
    override fun getInstance(algorithm: String, provider: Provider) = KeyPairGenerator.getInstance(algorithm, provider)
}