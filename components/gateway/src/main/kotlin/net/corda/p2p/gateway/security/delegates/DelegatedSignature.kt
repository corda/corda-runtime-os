package net.corda.p2p.gateway.security.delegates

import java.io.ByteArrayOutputStream
import java.security.AlgorithmParameters
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SignatureSpi
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.PSSParameterSpec

internal class DelegatedSignature(
    defaultHash: SigningService.Hash?,
    private val serviceMap: Map<PublicKey, SigningService?>
) : SignatureSpi() {
    private var alias: SigningService.Alias? = null
    private var publicKey: PublicKey? = null
    private var hash: SigningService.Hash? = defaultHash
    private val data = ByteArrayOutputStream()

    override fun engineInitSign(privateKey: PrivateKey?) {
        if (privateKey is DelegatedPrivateKey) {
            alias = privateKey.aliasService
        } else {
            throw UnsupportedOperationException()
        }
    }

    override fun engineSign(): ByteArray {
        return alias?.sign(
            hash ?: throw UnsupportedOperationException(),
            data.toByteArray()
        ) ?: throw UnsupportedOperationException()
    }

    override fun engineInitVerify(publicKey: PublicKey?) {
        if (!serviceMap.containsKey(publicKey)) {
            throw UnsupportedOperationException()
        }
        this.publicKey = publicKey
    }

    override fun engineVerify(sigBytes: ByteArray?): Boolean {
        return serviceMap[publicKey]?.verify(
            publicKey ?: throw UnsupportedOperationException(),
            hash ?: throw UnsupportedOperationException(),
            data.toByteArray(),
            sigBytes
        ) ?: throw UnsupportedOperationException()
    }

    override fun engineUpdate(b: Byte) {
        data.write(b.toInt())
    }

    override fun engineUpdate(b: ByteArray, off: Int, len: Int) {
        data.write(b, off, len)
    }

    override fun engineSetParameter(params: AlgorithmParameterSpec?) {
        if (params is PSSParameterSpec) {
            hash = SigningService.Hash
                .values()
                .firstOrNull {
                    it.hashName == params.digestAlgorithm
                }
        }
    }

    override fun engineGetParameters(): AlgorithmParameters? {
        return hash?.let { hash ->
            AlgorithmParameters.getInstance(SecurityDelegateProvider.RSA_SINGING_ALGORITHM).also {
                it.init(hash.rsaParameter)
            }
        }
    }

    override fun engineSetParameter(param: String?, value: Any?) {
        throw UnsupportedOperationException()
    }

    override fun engineGetParameter(param: String?): Any {
        throw UnsupportedOperationException()
    }
}
