package net.corda.v5.crypto

import java.security.PublicKey


/**
 * A simple data class for passing keys and weights into CompositeKeyGenerator
 *
 * @param node A public key
 * @param weight The weight for that key, must be greater than zero.
 */
data class CompositeKeyNodeAndWeight(val node: PublicKey, val weight: Int) {
    constructor(node: PublicKey): this(node, 1)
    init {
        // We don't allow zero or negative weights. Minimum weight = 1.
        require(weight > 0) { "A non-positive weight was detected. Member info: $this" }
    }
}
