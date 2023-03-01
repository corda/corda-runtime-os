package net.corda.virtualnode

import net.corda.crypto.core.ShortHash
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.security.MessageDigest

/**
 * Holding identity class - simply consists of two parts - the [x500Name] and the [groupId]
 *
 * @param x500Name The X500 name
 * @param groupId The group id associated with this holding identity
 */
data class HoldingIdentity(val x500Name: MemberX500Name, val groupId: String) {
    /**
     * Returns the holding identity as the first 12 characters of a SHA-256 hash of the x500 name and group id.
     *
     * To be used as a short look-up code that may be passed back to a customer as part of a URL etc.
     */
    val shortHash: ShortHash by lazy(LazyThreadSafetyMode.PUBLICATION) { ShortHash.of(hash) }

    /**
     * Returns the [SecureHash] of the holding identity: a SHA-256 hash of the x500 name and group.
     */
    val hash: SecureHash by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val s = (x500Name.toString() + groupId)
        val digest: MessageDigest = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
        val hash: ByteArray = digest.digest(s.toByteArray())
        SecureHash(DigestAlgorithmName.SHA2_256.name, hash)
    }

    /**
     * Returns the holding identity full hash (SHA-256 hash of the x500 name and group ID)
     */
    val fullHash: String by lazy(LazyThreadSafetyMode.PUBLICATION) { hash.toHexString() }
}

/** Convert the [HoldingIdentity] to an Avro representation */
fun HoldingIdentity.toAvro(): net.corda.data.identity.HoldingIdentity =
    net.corda.data.identity.HoldingIdentity(x500Name.toString(), groupId)

/** Convert the Avro representation of a holding identity back into [HoldingIdentity] */
fun net.corda.data.identity.HoldingIdentity.toCorda(): HoldingIdentity =
    HoldingIdentity(MemberX500Name.parse(x500Name), groupId)
