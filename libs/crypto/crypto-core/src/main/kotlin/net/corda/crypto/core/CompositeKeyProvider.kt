package net.corda.crypto.core

import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import java.security.PublicKey

// This is the internal lower level version of CompositeKeyGenerator (which is for use in flows)
interface CompositeKeyProvider {

    /* Return a composite key from a weighted list of keys, and an overall threshold (which can be null, in
    which case the threshold is the sum of the key weights) */
    fun create(keys: List<CompositeKeyNodeAndWeight>, threshold: Int?): PublicKey
}
