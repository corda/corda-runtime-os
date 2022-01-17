package com.r3.corda.utils.provider

import java.security.Provider

internal class DelegatedSignatureProvider : Provider(
    "DelegatedSignature",
    "0.2",
    "JCA/JCE Delegated Signature provider"
) {
    companion object {
        const val RSA_SINGING_ALGORITHM = "RSASSA-PSS"
    }
    init {
        putService(DelegatedSignatureService(RSA_SINGING_ALGORITHM, null))
        DelegatedSigningService.Hash.values().forEach {
            putService(DelegatedSignatureService(it.ecName, it))
        }
        this["AlgorithmParameters.EC"] = "sun.security.util.ECParameters"
    }

    private inner class DelegatedSignatureService(
        algorithm: String,
        private val defaultHash: DelegatedSigningService.Hash?,
    ) : Service(
        this@DelegatedSignatureProvider,
        "Signature",
        algorithm,
        DelegatedSignature::class.java.name,
        null,
        mapOf("SupportedKeyClasses" to DelegatedPrivateKey::class.java.name)
    ) {
        override fun newInstance(constructorParameter: Any?): Any {
            return DelegatedSignature(defaultHash)
        }
    }
}
