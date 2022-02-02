package net.corda.crypto.delegated.signing

import net.corda.crypto.delegated.signing.DelegatedSignerInstaller.Companion.RSA_SIGNING_ALGORITHM
import java.io.ByteArrayOutputStream
import java.security.AlgorithmParameters
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SignatureSpi
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.PSSParameterSpec

internal class DelegatedSignature(
    defaultHash: Hash?,
) : SignatureSpi() {
    private val data = ByteArrayOutputStream()
    private var hash: Hash? = defaultHash
    private var signingKey: DelegatedPrivateKey? = null

    override fun engineInitSign(privateKey: PrivateKey) {
        require(privateKey is DelegatedPrivateKey)
        data.reset()
        signingKey = privateKey
    }

    override fun engineUpdate(b: Byte) {
        data.write(b.toInt())
    }

    override fun engineUpdate(b: ByteArray, off: Int, len: Int) {
        data.write(b, off, len)
    }

    override fun engineSign(): ByteArray? {
        return try {
            val key = signingKey ?: throw SecurityException(
                "'engineSign' invoked without a key having been assigned previously via 'engineInitSign'"
            )
            key.signer.sign(
                key.publicKey,
                hash ?: throw SecurityException(
                    "'engineSign' invoked without a hash having been assigned previously via 'engineSetParameter'"
                ),
                data.toByteArray()
            )
        } finally {
            data.reset()
        }
    }

    override fun engineSetParameter(params: AlgorithmParameterSpec?) {
        if (params is PSSParameterSpec) {
            hash = Hash
                .values()
                .firstOrNull {
                    it.hashName == params.digestAlgorithm
                }
        }
    }

    override fun engineGetParameters(): AlgorithmParameters? {
        return hash?.let { hash ->
            AlgorithmParameters.getInstance(RSA_SIGNING_ALGORITHM).also {
                it.init(hash.rsaParameter)
            }
        }
    }

    override fun engineSetParameter(param: String?, value: Any?) = throw UnsupportedOperationException()
    override fun engineGetParameter(param: String?): Any = throw UnsupportedOperationException()
    override fun engineInitVerify(publicKey: PublicKey?) = throw UnsupportedOperationException()
    override fun engineVerify(sigBytes: ByteArray?): Boolean = throw UnsupportedOperationException()
}
