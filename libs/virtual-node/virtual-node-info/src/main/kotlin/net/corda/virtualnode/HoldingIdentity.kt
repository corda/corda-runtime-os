package net.corda.virtualnode

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.security.MessageDigest
import java.util.*

data class HoldingIdentity(val x500Name: String, val groupId: String) {
    /**
     * Returns the holding identity as the first 12 characters of a SHA-256 hash of the x500 name and group id.
     *
     * To be used as a short look-up code that may be passed back to a customer as part of a URL etc.
     */
    val id: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        hash.substring(0, 12)
    }

    /**
     * Returns the holding identity full hash (SHA-256 hash of the x500 name and group ID)
     */
    val hash: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val s = (x500Name + groupId)
        val digest: MessageDigest = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
        val hash: ByteArray = digest.digest(s.toByteArray())
        SecureHash(DigestAlgorithmName.SHA2_256.name, hash)
            .toHexString()
    }

    /** Vault DDL DB connection ID */
    var vaultDdlConnectionId: UUID? = null
    /** Vault DML DB connection ID */
    var vaultDmlConnectionId: UUID? = null
    /** Crypto DDL DB connection ID */
    var cryptoDdlConnectionId: UUID? = null
    /** Crypto DML DB connection ID */
    var cryptoDmlConnectionId: UUID? = null
    /** HSM connection ID */
    var hsmConnectionId: UUID? = null
}

fun HoldingIdentity.toAvro(): net.corda.data.identity.HoldingIdentity =
    net.corda.data.identity.HoldingIdentity(x500Name, groupId)

fun net.corda.data.identity.HoldingIdentity.toCorda(): HoldingIdentity =
    HoldingIdentity(x500Name, groupId)
