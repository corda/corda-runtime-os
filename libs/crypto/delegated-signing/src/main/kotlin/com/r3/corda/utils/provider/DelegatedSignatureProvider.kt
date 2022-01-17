package com.r3.corda.utils.provider

import java.security.NoSuchAlgorithmException
import java.security.Provider

class DelegatedSignatureProvider : Provider(
    "DelegatedSignature",
    "0.2",
    "JCA/JCE Delegated Signature provider"
) {
    init {
        val supportedHashingAlgorithm = listOf("SHA512", "SHA256", "SHA1", "NONE")
        val supportedSignatureAlgorithm = listOf("ECDSA", "RSA")

        for (hashingAlgorithm in supportedHashingAlgorithm) {
            for (signatureAlgorithm in supportedSignatureAlgorithm) {
                this.putService(DelegatedSignatureService(this, "${hashingAlgorithm}with$signatureAlgorithm"))
            }
        }
        this["AlgorithmParameters.EC"] = "sun.security.util.ECParameters"
    }

    class DelegatedSignatureService(provider: Provider, private val sigAlgo: String) : Service(
        provider, "Signature", sigAlgo, DelegatedSignature::class.java.name, null,
        mapOf("SupportedKeyClasses" to DelegatedPrivateKey::class.java.name)
    ) {
        @Throws(NoSuchAlgorithmException::class)
        override fun newInstance(var1: Any?): Any {
            return DelegatedSignature(sigAlgo)
        }
    }
}

