package com.r3.corda.notary.plugin.common

import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.notary.pluggable.NotarisationRequest
import net.corda.v5.ledger.notary.pluggable.NotarisationRequestSignature
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.membership.MemberInfo

data class NotarisationRequestImpl(
    // TODO should we move to `StateAndRef`?
    override val statesToConsume: List<StateRef>,
    override val transactionId: SecureHash
) : NotarisationRequest {

    /** Creates a signature over the notarisation request using the legal identity key. */
    // TODO CORE-3698 We shouldn't pass services to POJO classes
    fun generateSignature(
        memberInfo: MemberInfo,
        signingService: SigningService,
        serializationService: SerializationService
    ): NotarisationRequestSignature {
        val serializedRequest = serializationService.serialize(this).bytes
        val myLegalIdentity = memberInfo.sessionInitiationKey
        val signature = signingService.sign(
            serializedRequest,
            myLegalIdentity,
            SignatureSpec.ECDSA_SHA256 // TODO This shouldn't be hardcoded?
        )
        return NotarisationRequestSignatureImpl(signature, memberInfo.platformVersion)
    }

    /** Verifies the signature against this notarisation request. Checks that the signature is issued by the right party. */
    fun verifySignature(
        requestSignature: NotarisationRequestSignature,
        intendedSigner: Party,
        serializationService: SerializationService,
        signatureVerifier: DigitalSignatureVerificationService
    ) {
        val signature = requestSignature.digitalSignature
        require(intendedSigner.owningKey == signature.by) {
            "Expected a signature by ${intendedSigner.owningKey.toBase58String()}, but received by ${signature.by.toBase58String()}}"
        }

        // TODO CORE-3698: Review if this TODO is still valid
        //  if requestSignature was generated over an old version of NotarisationRequest, we need to be able to
        //  reserialize it in that version to get the exact same bytes. Modify the serialization logic once that's
        //  available.
        val expectedSignedBytes = serializationService.serialize(this).bytes
        signatureVerifier.verify(
            signature.by,
            SignatureSpec.ECDSA_SHA256, // TODO This shouldn't be hardcoded?
            signature.bytes,
            expectedSignedBytes
        )
    }
}
