package net.corda.ledger.common.flow.impl.transaction

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.core.bytes
import net.corda.internal.serialization.amqp.api.SerializationServiceInternal
import net.corda.ledger.common.data.transaction.SignableData
import net.corda.ledger.common.data.transaction.getBatchMerkleTreeDigestProvider
import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.ledger.common.flow.transaction.TransactionSignatureVerificationServiceInternal
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SignatureSpecService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.common.transaction.TransactionNoAvailableKeysException
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.v5.ledger.common.transaction.TransactionWithMetadata
import java.security.PublicKey
import java.time.Instant
import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl

@Suppress("LongParameterList")
class TransactionSignatureServiceImpl(
    private val serializationService: SerializationServiceInternal,
    private val signingService: SigningService,
    private val signatureSpecService: SignatureSpecService,
    private val merkleTreeProvider: MerkleTreeProvider,
    private val platformInfoProvider: PlatformInfoProvider,
    private val getCpiSummary: () -> CordaPackageSummaryImpl,
    private val transactionSignatureVerificationServiceInternal: TransactionSignatureVerificationServiceInternal
) : TransactionSignatureService,
    TransactionSignatureServiceInternal,
    TransactionSignatureVerificationServiceInternal by transactionSignatureVerificationServiceInternal {
    @Suspendable
    override fun sign(
        transaction: TransactionWithMetadata,
        publicKeys: Iterable<PublicKey>
    ): List<DigitalSignatureAndMetadata> {
        return getAvailableKeysFor(publicKeys).map { publicKey ->
            val signatureSpec = requireNotNull(signatureSpecService.defaultSignatureSpec(publicKey)) {
                "There are no available signature specs for this public key. ($publicKey ${publicKey.algorithm})"
            }
            val signatureMetadata = getSignatureMetadata(signatureSpec)
            val signableData = SignableData(transaction.id, signatureMetadata)
            val signature = signingService.sign(
                serializationService.serialize(signableData, withCompression = false).bytes,
                publicKey,
                signatureSpec
            )
            DigitalSignatureAndMetadata(signature, signatureMetadata)
        }
    }

    @Suspendable
    override fun signBatch(
        transactions: List<TransactionWithMetadata>,
        publicKeys: Iterable<PublicKey>
    ): List<List<DigitalSignatureAndMetadata>> {
        transactions.confirmBatchSigningRequirements()

        val publicKeysToSigSpecs = getAvailableKeysFor(publicKeys).associateWith { publicKey ->
            requireNotNull(signatureSpecService.defaultSignatureSpec(publicKey)) {
                "There are no available signature specs for this public key. ($publicKey ${publicKey.algorithm})"
            }
        }

        val hashDigestProvider = transactions.first().metadata.getBatchMerkleTreeDigestProvider(merkleTreeProvider)
        val batchTree = merkleTreeProvider.createTree(transactions.map { it.id.bytes }, hashDigestProvider)

        val batchSignaturesWithMeta = publicKeysToSigSpecs.map { (publicKey, signatureSpec) ->
            val signatureMetadata =
                getSignatureMetadata(signatureSpec, transactions.first().getBatchSignatureMetadataSettings())
            val signableData = SignableData(batchTree.root, signatureMetadata)

            signingService.sign(
                serializationService.serialize(signableData, withCompression = false).bytes,
                publicKey,
                signatureSpec
            ) to signatureMetadata
        }
        return List(transactions.size) {
            val proof = batchTree.createAuditProof(listOf(it))
            batchSignaturesWithMeta.map { (signature, signatureMetadata) ->
                DigitalSignatureAndMetadata(signature, signatureMetadata, proof)
            }
        }
    }

    @Suspendable
    private fun getAvailableKeysFor(publicKeys: Iterable<PublicKey>): List<PublicKey> {
        val availableKeys = signingService.findMySigningKeys(publicKeys.toSet()).values.filterNotNull()
        if (availableKeys.isEmpty()) {
            throw TransactionNoAvailableKeysException(
                "Cannot sign transaction(s) - no private keys were found for the requested public keys.",
                null
            )
        }
        return availableKeys
    }

    private fun getSignatureMetadata(
        signatureSpec: SignatureSpec,
        batchSettings: Map<String, String> = emptyMap()
    ): DigitalSignatureMetadata {
        val cpiSummary = getCpiSummary()
        return DigitalSignatureMetadata(
            Instant.now(),
            signatureSpec,
            mapOf(
                "platformVersion" to platformInfoProvider.activePlatformVersion.toString(),
                "cpiName" to cpiSummary.name,
                "cpiVersion" to cpiSummary.version,
                "cpiSignerSummaryHash" to cpiSummary.signerSummaryHash.toString(),
                "cpiFileChecksum" to cpiSummary.fileChecksum.toString()
            ) + batchSettings
        )
    }
}
