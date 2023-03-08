package net.corda.crypto.softhsm

import java.security.KeyPairGenerator
import java.security.Provider

fun interface KeyPairGeneratorFactory {
    fun getInstance(algorithm: String, provider: Provider): KeyPairGenerator
}
