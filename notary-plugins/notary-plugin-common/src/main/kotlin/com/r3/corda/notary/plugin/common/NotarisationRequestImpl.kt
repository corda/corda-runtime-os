package com.r3.corda.notary.plugin.common

import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.notary.plugin.core.NotarisationRequest
import net.corda.v5.ledger.notary.plugin.core.NotarisationRequestSignature
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.membership.MemberInfo

data class NotarisationRequestImpl(
    override val statesToConsume: List<StateRef>,
    override val transactionId: SecureHash
) : NotarisationRequest
