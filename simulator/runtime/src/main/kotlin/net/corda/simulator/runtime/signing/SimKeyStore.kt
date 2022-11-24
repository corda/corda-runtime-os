package net.corda.simulator.runtime.signing

import net.corda.simulator.crypto.HsmCategory
import java.security.PublicKey

/**
 * Generates a key for use in simulating signing. Each member has one SimKeyStore in which all their keys are stored.
 */
interface SimKeyStore {

    /**
     * Generates an ECDSA key, regardless of the parameters used. Stores that key along with these parameters
     * for use in simulated signing and verification. Note that these are the parameters which would commonly be passed
     * to the Key Management API in real Corda.
     *
     * @param alias An alias for the key.
     * @param hsmCategory The HSM category for the key.
     * @param scheme The scheme for the key; note that this is not used in generating the key.
     * @return An ECDSA public key.
     */
    fun generateKey(alias: String, hsmCategory: HsmCategory, scheme: String) : PublicKey

    /**
     * Retrieves the parameters which were stored for the given public key.
     *
     * @param publicKey The key for which to retrieve parameters.
     * @return The parameters for this key, or null if the key is not in this store.
     */
    fun getParameters(publicKey: PublicKey): KeyParameters?
}
