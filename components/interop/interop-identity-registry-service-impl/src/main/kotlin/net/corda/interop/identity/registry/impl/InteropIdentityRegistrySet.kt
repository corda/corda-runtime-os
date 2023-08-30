package net.corda.interop.identity.registry.impl

import net.corda.crypto.core.ShortHash
import net.corda.interop.core.InteropIdentity

/**
 * Set-like container for InteropIdentities which keys off of short hash and ignores other properties.
 */
class InteropIdentityRegistrySet {
    private val identities = HashMap<ShortHash, InteropIdentity>()

    val size get() = identities.size
    val values get() = identities.values

    fun toSet() = identities.values.toSet()

    fun get(shortHash: ShortHash) = identities[shortHash]

    fun add(identity: InteropIdentity) {
        identities[identity.shortHash] = identity
    }

    fun remove(identity: InteropIdentity) = identities.remove(identity.shortHash)
}
