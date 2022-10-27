package com.r3.corda.notary.plugin.common

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.ledger.notary.plugin.core.NotarisationResponse
import net.corda.v5.ledger.notary.plugin.core.NotaryError

data class NotarisationResponseImpl(
    override val signatures: List<DigitalSignatureAndMetadata>,
    override val error: NotaryError?
) : NotarisationResponse
