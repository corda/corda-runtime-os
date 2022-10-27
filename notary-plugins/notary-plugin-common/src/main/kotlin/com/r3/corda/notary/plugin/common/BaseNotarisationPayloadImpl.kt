package com.r3.corda.notary.plugin.common

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.notary.plugin.api.NotarisationPayload
import net.corda.v5.ledger.notary.plugin.core.NotarisationRequestSignature

/**
 * A generic [NotarisationPayload] implementation that acts as a "base" class for multiple notarisation payloads.
 * It runs validation that checks if the given [transaction]'s type is actually one of [validTypes].
 */
@CordaSerializable
abstract class BaseNotarisationPayloadImpl(
    override val transaction: Any,
    override val requestSignature: NotarisationRequestSignature,
    override val validTypes: List<Class<*>>
) : NotarisationPayload {

    init {
        require(validTypes.any { it.isAssignableFrom(transaction::class.java) }) {
            "Unexpected transaction type ${transaction::class.java} in " +
                    "notarisation payload. There may be a mismatch " +
                    "between the configured notary type and the one " +
                    "advertised on the network parameters."
        }
    }
}
