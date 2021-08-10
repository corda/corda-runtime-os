package net.corda.p2p.gateway.messaging.http

import net.corda.p2p.NetworkType
import java.security.MessageDigest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.net.URI

object SniCalculator {

    private const val HASH_ALGO = "SHA-256"
    private const val HASH_TRUNCATION_SIZE = 63 //Truncate to 63 characters (as RCF 1035)
    private const val CLASSIC_CORDA_SNI_SUFFIX = ".p2p.corda.net" //This is intentionally different to Corda 4

    fun calculateSni(
        x500Name: String,
        networkType: NetworkType,
        address: String
    ): String {
        return when (networkType) {
            NetworkType.CORDA_4 -> {
                sha256Hash(x500Name.toByteArray()).toHex().take(HASH_TRUNCATION_SIZE)
                    .toLowerCase() + CLASSIC_CORDA_SNI_SUFFIX
            }
            NetworkType.CORDA_5 -> {
                URI.create(address).host
            }
        }
    }

    private fun sha256Hash(bytes: ByteArray): ByteArray {
        val provider = BouncyCastleProvider()
        val messageDigest = MessageDigest.getInstance(HASH_ALGO, provider)
        messageDigest.reset()
        messageDigest.update(bytes)
        return messageDigest.digest()
    }
}