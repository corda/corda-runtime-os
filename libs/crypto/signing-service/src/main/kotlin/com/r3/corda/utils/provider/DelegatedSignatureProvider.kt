package com.r3.corda.utils.provider

import java.io.ByteArrayOutputStream
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.SignatureSpi

@Suppress("DEPRECATION")    // JDK11: should replace with Provider(String name, String versionStr, String info) (since 9)
class DelegatedSignatureProvider : Provider("DelegatedSignature", 0.1, "JCA/JCE Delegated Signature provider") {
    companion object {
        // JDK11 has sun.security.util.ECParameters, some JDK8 distributions have also switched
        // to sun.security.util.ECParameters from update 272.
        // Future - This dependency should be removed.
        val ecAlgorithmParametersClass: String by lazy {
            val ecECParametersClass = "sun.security.ec.ECParameters"
            val utilECParametersClass = "sun.security.util.ECParameters"
            try {
                this::class.java.classLoader.loadClass(ecECParametersClass)
                ecECParametersClass
            }
            catch (ex: ClassNotFoundException) {
                this::class.java.classLoader.loadClass(utilECParametersClass)
                utilECParametersClass
            }
        }
    }
    init {
        val supportedHashingAlgorithm = listOf("SHA512", "SHA256", "SHA1", "NONE")
        val supportedSignatureAlgorithm = listOf("ECDSA", "RSA")

        for (hashingAlgorithm in supportedHashingAlgorithm) {
            for (signatureAlgorithm in supportedSignatureAlgorithm) {
                this.putService(DelegatedSignatureService(this, "${hashingAlgorithm}with$signatureAlgorithm"))
            }
        }
        this["AlgorithmParameters.EC"] = ecAlgorithmParametersClass
    }

    class DelegatedSignatureService(provider: Provider, private val sigAlgo: String) : Service(provider, "Signature", sigAlgo, DelegatedSignature::class.java.name, null,
            mapOf("SupportedKeyClasses" to DelegatedPrivateKey::class.java.name)) {
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
        signingKey = privateKey as DelegatedPrivateKey
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