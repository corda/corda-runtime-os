package net.corda.crypto.delegated.signing

import net.corda.v5.crypto.ParameterizedSignatureSpec
import net.corda.v5.crypto.SignatureSpec
import java.io.ByteArrayOutputStream
import java.security.AlgorithmParameters
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SignatureSpi
import java.security.spec.AlgorithmParameterSpec

internal class DelegatedSignature(
    private val signatureName: String,
) : SignatureSpi() {
    private val data = ByteArrayOutputStream()
    private var signingKey: DelegatedPrivateKey? = null
    private var parameterSpec: AlgorithmParameterSpec? = null

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
        val spec = if (parameterSpec != null) {
            ParameterizedSignatureSpec(signatureName, parameterSpec!!)
        } else {
            SignatureSpec(signatureName)
        }
        return try {
            val key = signingKey ?: throw SecurityException(
                "'engineSign' invoked without a key having been assigned previously via 'engineInitSign'"
            )
            key.signer.sign(
                key.publicKey,
                spec,
                data.toByteArray()
            )
        } finally {
            data.reset()
        }
    }

    override fun engineSetParameter(params: AlgorithmParameterSpec?) {
        parameterSpec = params
    }

    override fun engineGetParameters(): AlgorithmParameters? {
        return parameterSpec?.let { parameter ->
            AlgorithmParameters.getInstance(signatureName).also {
                it.init(parameter)
            }
        }
    }

    override fun engineSetParameter(param: String?, value: Any?) = throw UnsupportedOperationException()
    override fun engineGetParameter(param: String?): Any = throw UnsupportedOperationException()
    override fun engineInitVerify(publicKey: PublicKey?) = throw UnsupportedOperationException()
    override fun engineVerify(sigBytes: ByteArray?): Boolean = throw UnsupportedOperationException()
}
