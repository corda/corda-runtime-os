package net.corda.ledger.common.flow.impl.transaction

import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.ledger.common.data.transaction.SignableData
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SignatureSpecService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.security.PublicKey
import java.time.Instant

@Component(service = [TransactionSignatureService::class, SingletonSerializeAsToken::class, UsedByFlow::class], scope = ServiceScope.PROTOTYPE)
class TransactionSignatureServiceImpl @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = SigningService::class)
    private val signingService: SigningService,
    @Reference(service = DigitalSignatureVerificationService::class)
    private val digitalSignatureVerificationService: DigitalSignatureVerificationService,
    @Reference(service = SignatureSpecService::class)
    private val signatureSpecService: SignatureSpecService
) : TransactionSignatureService, SingletonSerializeAsToken, UsedByFlow {
    @Suspendable
    override fun sign(txId: SecureHash, publicKey: PublicKey): DigitalSignatureAndMetadata {
        val signatureMetadata = getSignatureMetadata()
        val signableData = SignableData(txId, signatureMetadata)
        val signature = signingService.sign(
            serializationService.serialize(signableData).bytes,
            publicKey,
            SignatureSpec.ECDSA_SHA256
        ) //Rework with CORE-6969
        return DigitalSignatureAndMetadata(signature, signatureMetadata)
    }

    override fun verifySignature() {
        TODO("Not yet implemented")
    }

    private fun getSignatureMetadata(): DigitalSignatureMetadata {
        val cpiSummary = getCpiSummary()
        return DigitalSignatureMetadata(
            Instant.now(),
            linkedMapOf(
                "cpiName" to cpiSummary.name,
                "cpiVersion" to cpiSummary.version,
                "cpiSignerSummaryHash" to cpiSummary.signerSummaryHash.toString()
            )
        )
    }
}

/**
 * TODO [CORE-7126] Fake values until we can get CPI information properly
 */
private fun getCpiSummary(): CordaPackageSummary =
    CordaPackageSummary(
        name = "CPI name",
        version = "CPI version",
        signerSummaryHash = SecureHash("SHA-256", "Fake-value".toByteArray()).toHexString(),
        fileChecksum = SecureHash("SHA-256", "Another-Fake-value".toByteArray()).toHexString()
    )