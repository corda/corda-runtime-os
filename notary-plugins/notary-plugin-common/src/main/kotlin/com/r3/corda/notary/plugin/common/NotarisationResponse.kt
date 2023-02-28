package com.r3.corda.notary.plugin.common

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.notary.plugin.core.NotaryException

/**
 * A data class that represents the payload returned by the notary server flow to the client flow. Contains the
 * signatures from the server side and the error, if applicable.
 */
@CordaSerializable
class NotarisationResponse(
    val signatures: List<DigitalSignatureAndMetadata>,
    val error: NotaryException?
)
