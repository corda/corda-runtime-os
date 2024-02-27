package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.membership.lib.SignedGroupParameters
import net.corda.v5.crypto.SecureHash

/**
 * Cache for group parameters.
 *
 * This includes the current group parameters after they are requested by transaction creation.
 *
 * Historic group parameters are held within the cache once requested by existing transactions, during transaction verification.
 */
interface GroupParametersCache {

    /**
     * Gets the cached [SignedGroupParameters] for the input [hash].
     *
     * @param hash The hash of the [SignedGroupParameters] to find.
     *
     * @return The cached [SignedGroupParameters], or `null` if they do not exist.
     *
     */
    fun get(hash: SecureHash): SignedGroupParameters?

    /**
     * Put a [SignedGroupParameters] into the cache.
     *
     * @param groupParameters The [SignedGroupParameters] to cache.
     */
    fun put(groupParameters: SignedGroupParameters)
}
