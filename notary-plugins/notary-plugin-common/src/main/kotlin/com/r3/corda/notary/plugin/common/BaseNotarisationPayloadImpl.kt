package com.r3.corda.notary.plugin.common

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.notary.plugin.api.NotarisationPayload
import net.corda.v5.ledger.notary.plugin.core.NotarisationRequestSignature

/**
 * A generic [NotarisationPayload] implementation that acts as a "base" class for multiple notarisation payloads.
 * It runs a validation that checks if the given [transaction]'s type is actually one of [validTypes].
 */
@CordaSerializable
abstract class BaseNotarisationPayloadImpl(
    final override val transaction: Any,
    final override val requestSignature: NotarisationRequestSignature,
    final override val validTypes: List<Class<*>>
) : NotarisationPayload {

    init {
        require(validTypes.any { it.isAssignableFrom(transaction::class.java) })
    }

    /**
     * Helper function used in multiple implementations. This function will simply throw an IllegalArgumentException
     * with a pre-defined message which is related to the fact that the [transaction]'s object type is unexpected.
     */
    protected fun unexpectedTransactionType() = IllegalArgumentException("Unexpected transaction type in the notarisation payload: " +
            "${transaction::class.java}, it may be that there is a discrepancy between the configured notary type " +
            "(validating/non-validating) and the one advertised on the network parameters."
    )
}
