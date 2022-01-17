package com.r3.corda.utils.provider

import java.io.ByteArrayOutputStream
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.SignatureSpi

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

class DelegatedSignature(private val sigAlgo: String) : SignatureSpi() {
    private val data = ByteArrayOutputStream()
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
            signingKey?.sign(sigAlgo, data.toByteArray())
        } finally {
            data.reset()
        }
    }

    override fun engineSetParameter(param: String?, value: Any?) = throw UnsupportedOperationException()
    override fun engineGetParameter(param: String?): Any = throw UnsupportedOperationException()
    override fun engineInitVerify(publicKey: PublicKey?) = throw UnsupportedOperationException()
    override fun engineVerify(sigBytes: ByteArray?): Boolean = throw UnsupportedOperationException()
}
