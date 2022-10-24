package com.r3.corda.notary.plugin.common

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.uniqueness.model.UniquenessCheckError
import net.corda.v5.ledger.notary.pluggable.NotarisationResponse

data class NotarisationResponseImpl(
    override val signatures: List<DigitalSignatureAndMetadata>,
    override val error: UniquenessCheckError?
) : NotarisationResponse
