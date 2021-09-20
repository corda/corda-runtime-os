package net.corda.cipher.suite.impl

import net.corda.utilities.LazyPool
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.cipher.suite.schemes.SignatureSpec
import java.security.Provider
import java.security.Signature
import java.util.concurrent.ConcurrentHashMap

/**
 * This is a collection of [Signature] instances as the [Signature.getInstance] method tend to be quite inefficient
 * and we want to be able to optimise them en masse.
 */
class SignatureInstances(
    private val providers: Map<String, Provider>
) {
    private val signatureFactory: SignatureFactory = CachingSignatureFactory()

    fun <A> withSignature(signatureScheme: SignatureScheme, signatureSpec: SignatureSpec, func: (signature: Signature) -> A): A {
        val signature = getSignatureInstance(signatureSpec.signatureName, providers[signatureScheme.providerName])
        try {
            return func(signature)
        } finally {
            releaseSignatureInstance(signature)
        }
    }

    private fun getSignatureInstance(algorithm: String, provider: Provider?) = signatureFactory.borrow(algorithm, provider)
    private fun releaseSignatureInstance(sig: Signature) = signatureFactory.release(sig)

    // The provider itself is a very bad key class as hashCode() is expensive and contended.
    // So use name and version instead.
    private data class SignatureKey(
        val algorithm: String,
        val providerName: String?,
        val providerVersion: String?
    ) {
        constructor(algorithm: String, provider: Provider?) : this(algorithm, provider?.name, provider?.versionStr)
    }

    private class CachingSignatureFactory : SignatureFactory {
        private val signatureInstances = ConcurrentHashMap<SignatureKey, LazyPool<Signature>>()

        override fun borrow(algorithm: String, provider: Provider?): Signature {
            return signatureInstances.getOrPut(SignatureKey(algorithm, provider)) {
                LazyPool { Signature.getInstance(algorithm, provider) }
            }.borrow()
        }

        override fun release(sig: Signature): Unit =
            signatureInstances[SignatureKey(sig.algorithm, sig.provider)]?.release(sig)!!
    }

    interface SignatureFactory {
        fun borrow(algorithm: String, provider: Provider?): Signature
        fun release(sig: Signature) {}
    }
}
