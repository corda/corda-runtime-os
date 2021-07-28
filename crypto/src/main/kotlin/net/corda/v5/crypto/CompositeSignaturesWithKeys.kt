package net.corda.v5.crypto

import net.corda.v5.base.annotations.CordaSerializable

/**
 * Custom class for holding signature data. This exists for later extension work to provide a standardised cross-platform
 * serialization format.
 */
@CordaSerializable
data class CompositeSignaturesWithKeys(val sigs: List<DigitalSignature.WithKey>) {
    companion object {
        @JvmField
        val EMPTY = CompositeSignaturesWithKeys(emptyList())
    }
}
