package com.r3.corda.notary.plugin.common

import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.ledger.notary.plugin.core.NotarisationRequestSignature

data class NotarisationRequestSignatureImpl(
    override val digitalSignature: DigitalSignature.WithKey,
    override val platformVersion: Int
) : NotarisationRequestSignature
