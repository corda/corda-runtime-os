package net.corda.simulator.runtime.signing

import net.corda.simulator.crypto.HsmCategory

/**
 * Data record for the parameters that would normally be provided to Corda's Key Management API.
 *
 * @param alias The alias to assign to the key.
 * @param hsmCategory The HSM category to use.
 * @param scheme The scheme to use in verification; note that in Simulator this is not used to generate a key.
 */
data class KeyParameters(val alias: String, val hsmCategory: HsmCategory, val scheme: String)