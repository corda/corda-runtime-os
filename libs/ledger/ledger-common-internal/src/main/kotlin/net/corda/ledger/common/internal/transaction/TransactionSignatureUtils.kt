package net.corda.ledger.common.internal.transaction

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey
import java.time.Instant

//TODO(Should this be another service? Or could we just squeeze this into the signingservice?)

private fun getSignatureMetadata(cpiIdentifier: CpiIdentifier): DigitalSignatureMetadata {
    return DigitalSignatureMetadata(
        Instant.now(),
        linkedMapOf(
            "cpiName" to cpiIdentifier.name,
            "cpiVersion" to cpiIdentifier.version,
            "cpiSignerSummaryHash" to cpiIdentifier.signerSummaryHash.toString()
        )
    )
}

@Suspendable
fun createTransactionSignature(
    signingService: SigningService,
    serializationService: SerializationService,
    cpiIdentifier: CpiIdentifier,
    txId: SecureHash,
    publicKey: PublicKey
): DigitalSignatureAndMetadata {
    val signatureMetadata = getSignatureMetadata(cpiIdentifier)
    val signableData = SignableData(txId, signatureMetadata)
    val signature = signingService.sign(
        serializationService.serialize(signableData).bytes,
        publicKey,
        SignatureSpec.ECDSA_SHA256
    ) //Rework with CORE-6969
    return DigitalSignatureAndMetadata(signature, signatureMetadata)
}
