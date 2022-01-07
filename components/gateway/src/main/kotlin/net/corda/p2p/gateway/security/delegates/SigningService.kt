package net.corda.p2p.gateway.security.delegates

import java.security.PublicKey
import java.security.cert.Certificate
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec

interface SigningService {
    val aliases: Collection<Alias>
    enum class Hash(val hashName: String, val saltLength: Int) {
        SHA256("SHA-256", 32),
        SHA384("SHA-384", 48),
        SHA512("SHA-512", 64);

        val rsaParameter by lazy {
            PSSParameterSpec(
                hashName,
                "MGF1",
                MGF1ParameterSpec(hashName),
                saltLength,
                1
            )
        }

        val ecName = "${name}withECDSA"
    }
    fun verify(publicKey: PublicKey, hash: Hash, data: ByteArray, signature: ByteArray?): Boolean

    interface Alias {
        val name: String
        val certificates: Collection<Certificate>

        fun sign(hash: Hash, data: ByteArray): ByteArray
    }
}
