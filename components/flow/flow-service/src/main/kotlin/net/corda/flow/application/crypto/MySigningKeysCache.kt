package net.corda.flow.application.crypto

import java.security.PublicKey

/**
 * Cache for a virtual nodes signing keys.
 */
interface MySigningKeysCache {

    /**
     * Gets the cached signing keys matching to the input [keys].
     *
     * @param keys The signing keys to retrieve from the cache.
     * @return A mapping that maps the requested signing key:
     *      * <ul>
     *      *     <li> to the same key if it is owned by the caller in case the requested signing key is a plain key </li>
     *      *     <li> to the firstly found composite key leaf to be owned by the caller, of the composite key's leaves (children)
     *      *     in case the requested signing key is a composite key </li>
     *      *     <li> to {@code null} if the requested key is not owned by the caller </li>
     *      * </ul>
     */
    fun get(keys: Set<PublicKey>): Map<PublicKey, PublicKey?>

    /**
     * Put signing keys into the cache.
     *
     * @param keys The signing keys to cache.
     */
    fun putAll(keys: Map<out PublicKey, PublicKey?>)
}