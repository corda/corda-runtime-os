package net.corda.ledger.common.data.transaction

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey
import java.time.Instant

//TODO(CORE-6969 Place this to somewhere else, potentially into a new Service. )
@Suspendable
fun createTransactionSignature(
    signingService: SigningService,
    serializationService: SerializationService,
    cpiSummary: CordaPackageSummary,
    txId: SecureHash,
    publicKey: PublicKey
): DigitalSignatureAndMetadata {
    val signatureMetadata = getSignatureMetadata(cpiSummary)
    val signableData = SignableData(txId, signatureMetadata)
    val signature = signingService.sign(
        serializationService.serialize(signableData).bytes,
        publicKey,
        SignatureSpec.ECDSA_SHA256
    ) //Rework with CORE-6969
    return DigitalSignatureAndMetadata(signature, signatureMetadata)
}

private fun getSignatureMetadata(cpiSummary: CordaPackageSummary): DigitalSignatureMetadata {
    return DigitalSignatureMetadata(
        Instant.now(),
        linkedMapOf(
            "cpiName" to cpiSummary.name,
            "cpiVersion" to cpiSummary.version,
            "cpiSignerSummaryHash" to cpiSummary.signerSummaryHash.toString()
        )
    )
}
