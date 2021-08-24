package net.corda.p2p.gateway.keystore

import java.io.ByteArrayOutputStream
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.SignatureSpi
import java.security.spec.AlgorithmParameterSpec

/**
 * Registers [DelegatedSignature] which is used in conjunction with [DelegatedPrivateKey].
 */
class DelegatedSignatureProvider : Provider("DelegatedSignature", "1.0", "JCA/JCE Delegated Signature provider") {

    init {
        val supportedHashingAlgorithm = listOf("SHA512", "SHA256", "SHA1", "NONE")
        val supportedSignatureAlgorithm = listOf("ECDSA", "RSA")

        for (hashingAlgorithm in supportedHashingAlgorithm) {
            for (signatureAlgorithm in supportedSignatureAlgorithm) {
                this.putService(DelegatedSignatureService(this, "${hashingAlgorithm}with$signatureAlgorithm"))
            }
        }

        this.putService(DelegatedSignatureService(this, "RSASSA-PSS"))
        this["AlgorithmParameters.EC"] = "sun.security.util.ECParameters"
    }

    class DelegatedSignatureService(provider: Provider, private val sigAlgo: String) :
            Service(provider, "Signature", sigAlgo, DelegatedSignature::class.java.name, null,
                    mapOf("SupportedKeyClasses" to DelegatedPrivateKey::class.java.name)) {

        @Throws(NoSuchAlgorithmException::class)
        override fun newInstance(var1: Any?): Any = DelegatedSignature(sigAlgo)
    }
}

/**
 * Can sign using [DelegatedPrivateKey].
 */
class DelegatedSignature(private val sigAlgo: String) : SignatureSpi() {
    private val data = ByteArrayOutputStream()
    private var signingKey: DelegatedPrivateKey? = null

    override fun engineInitSign(privateKey: PrivateKey) {
        require(privateKey is DelegatedPrivateKey)
        data.reset()
        signingKey = privateKey
    }

    override fun engineSetParameter(params: AlgorithmParameterSpec) {
        // Not implementing this causes the signer retrieval to throw
    }

    override fun engineUpdate(b: Byte) {
        data.write(b.toInt())
    }

    override fun engineUpdate(b: ByteArray, off: Int, len: Int) {
        data.write(b, off, len)
    }

    override fun engineSign(): ByteArray? = try {
        signingKey?.sign(sigAlgo, data.toByteArray())
    } finally {
        data.reset()
    }

    override fun engineSetParameter(param: String?, value: Any?) = throw UnsupportedOperationException()
    override fun engineGetParameter(param: String?): Any = throw UnsupportedOperationException()
    override fun engineInitVerify(publicKey: PublicKey?) = throw UnsupportedOperationException()
    override fun engineVerify(sigBytes: ByteArray?): Boolean = throw UnsupportedOperationException()
}