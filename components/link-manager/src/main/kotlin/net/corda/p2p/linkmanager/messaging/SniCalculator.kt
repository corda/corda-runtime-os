package net.corda.p2p.linkmanager.messaging

import net.corda.p2p.HoldingIdentity
import java.security.MessageDigest
import org.bouncycastle.jce.provider.BouncyCastleProvider

class SniCalculator {

    companion object {

        private const val HASH_ALGO = "SHA-256"
        private const val HASH_TRUNCATION_SIZE = 63 //Truncate to 63 characters (as RCF 1035)
        private const val ADDRESS_DELIMITER = ":"

        fun calculateSni(peer: HoldingIdentity, address: String): String {
            //Classic Corda Identity
            return if (peer.groupId == null) {
                sha256Hash(peer.x500Name.toByteArray()).toString().take(HASH_TRUNCATION_SIZE).toLowerCase()
            } else {
                //We assume address is made up of the HostName and Port e.g. MyNode:80
                address.split(ADDRESS_DELIMITER)[0]
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

}