package net.corda.crypto.delegated.signing

import net.corda.crypto.delegated.signing.DelegatedSigningService.Companion.RSA_SIGNING_ALGORITHM
import java.security.Provider

internal class DelegatedSignatureProvider : Provider(
    "DelegatedSignature",
    "0.2",
    "JCA/JCE Delegated Signature provider"
) {
    init {
        putService(DelegatedSignatureService(RSA_SIGNING_ALGORITHM, null))
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
