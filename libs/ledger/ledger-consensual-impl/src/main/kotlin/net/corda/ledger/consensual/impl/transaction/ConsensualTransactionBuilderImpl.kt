package net.corda.ledger.consensual.impl.transaction

import net.corda.ledger.common.impl.transaction.CpiSummary
import net.corda.ledger.common.impl.transaction.CpkSummary
import java.security.PublicKey
import java.time.Instant
import net.corda.ledger.common.impl.transaction.PrivacySaltImpl
import net.corda.ledger.common.internal.transaction.SignableData
import net.corda.ledger.common.impl.transaction.TransactionMetaData
import net.corda.ledger.common.impl.transaction.TransactionMetaData.Companion.CPI_METADATA_KEY
import net.corda.ledger.common.impl.transaction.TransactionMetaData.Companion.CPK_METADATA_KEY
import net.corda.ledger.common.impl.transaction.TransactionMetaData.Companion.DIGEST_SETTINGS_KEY
import net.corda.ledger.common.impl.transaction.TransactionMetaData.Companion.LEDGER_MODEL_KEY
import net.corda.ledger.common.impl.transaction.TransactionMetaData.Companion.LEDGER_VERSION_KEY
import net.corda.ledger.common.impl.transaction.TransactionMetaData.Companion.PLATFORM_VERSION_KEY
import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.ledger.common.impl.transaction.WireTransactionDigestSettings
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder

// TODO Create an AMQP serializer if we plan on sending transaction builders between virtual nodes
@Suppress("LongParameterList")
class ConsensualTransactionBuilderImpl(
    private val cipherSchemeMetadata: CipherSchemeMetadata,
    private val digestService: DigestService,
    private val jsonMarshallingService: JsonMarshallingService,
    private val merkleTreeProvider: MerkleTreeProvider,
    private val serializationService: SerializationService,
    private val signingService: SigningService,
    // cpi defines what type of signing/hashing is used (related to the digital signature signing and verification stuff)
    private val memberLookup: MemberLookup,
    private val sandboxCpks: List<CpkMetadata>,
    override val states: List<ConsensualState> = emptyList(),
) : ConsensualTransactionBuilder {

    override fun withStates(vararg states: ConsensualState): ConsensualTransactionBuilder =
        this.copy(states = this.states + states)

    @Suspendable
    override fun signInitial(publicKey: PublicKey): ConsensualSignedTransaction {
        val wireTransaction = buildWireTransaction()
        val signatureWithMetaData = createSignature(wireTransaction.id, publicKey)
        return ConsensualSignedTransactionImpl(serializationService, wireTransaction, listOf(signatureWithMetaData))
    }

    private fun getSignatureMetadata(): DigitalSignatureMetadata {
        val cpi = getCpiIdentifier()
        return DigitalSignatureMetadata(
            Instant.now(),
            linkedMapOf(
                "cpiName" to cpi.name,
                "cpiVersion" to cpi.version,
                "cpiSignerSummaryHash" to cpi.signerSummaryHash.toString()
            )
        )
    }

    @Suspendable
    private fun createSignature(txId: SecureHash, publicKey: PublicKey): DigitalSignatureAndMetadata {
        val signatureMetadata = getSignatureMetadata()
        val signableData = SignableData(txId, signatureMetadata)
        val signature = signingService.sign(
            serializationService.serialize(signableData).bytes,
            publicKey,
            SignatureSpec.ECDSA_SHA256
        ) //Rework with CORE-6969
        return DigitalSignatureAndMetadata(signature, signatureMetadata)
    }

    private fun buildWireTransaction(): WireTransaction {
        // TODO(CORE-5982 more verifications)
        // TODO(CORE-5940 ? metadata verifications: nulls, order of CPKs, at least one CPK?))
        require(states.isNotEmpty()) { "At least one consensual state is required" }
        require(states.all { it.participants.isNotEmpty() }) { "All consensual states must have participants" }
        val componentGroupLists = calculateComponentGroupLists(serializationService)

        val entropy = ByteArray(32)
        cipherSchemeMetadata.secureRandom.nextBytes(entropy)
        val privacySalt = PrivacySaltImpl(entropy)

        return WireTransaction(
            merkleTreeProvider,
            digestService,
            jsonMarshallingService,
            privacySalt,
            componentGroupLists
        )
    }

    private fun calculateComponentGroupLists(serializer: SerializationService): List<List<ByteArray>> {
        val requiredSigningKeys = states
            .map { it.participants }
            .flatten()
            .map { it.owningKey }
            .distinct()

        val componentGroupLists = mutableListOf<List<ByteArray>>()
        for (componentGroupIndex in ConsensualComponentGroupEnum.values()) {
            componentGroupLists += when (componentGroupIndex) {
                ConsensualComponentGroupEnum.METADATA ->
                    listOf(
                        jsonMarshallingService.format(calculateMetaData())
                            .toByteArray(Charsets.UTF_8)
                    ) // TODO(update with CORE-5940)
                ConsensualComponentGroupEnum.TIMESTAMP ->
                    listOf(serializer.serialize(Instant.now()).bytes)
                ConsensualComponentGroupEnum.REQUIRED_SIGNING_KEYS ->
                    requiredSigningKeys.map { serializer.serialize(it).bytes }
                ConsensualComponentGroupEnum.OUTPUT_STATES ->
                    states.map { serializer.serialize(it).bytes }
                ConsensualComponentGroupEnum.OUTPUT_STATE_TYPES ->
                    states.map { serializer.serialize(it::class.java.name).bytes }
            }
        }
        return componentGroupLists
    }

    private fun calculateMetaData(): TransactionMetaData {
        return TransactionMetaData(
            linkedMapOf(
                LEDGER_MODEL_KEY to ConsensualLedgerTransactionImpl::class.java.canonicalName,
                LEDGER_VERSION_KEY to TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION,
                DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
                PLATFORM_VERSION_KEY to memberLookup.myInfo().platformVersion,
                CPI_METADATA_KEY to getCpiMetadata(),
                CPK_METADATA_KEY to getCpkMetadata()
            )
        )
    }

    /**
     * TODO(Fake values until we can get CPI information properly)
     */
    private fun getCpiIdentifier(): CpiIdentifier {
        return CpiIdentifier(
            "CPI name",
            "CPI version",
            SecureHash("SHA-256", "Fake-value".toByteArray())
        )
    }

    private fun getCpiMetadata(): CpiSummary {
        val cpiIdentifier = getCpiIdentifier()

        return CpiSummary(
            name = cpiIdentifier.name,
            version = cpiIdentifier.version,
            signerSummaryHash = cpiIdentifier.signerSummaryHash?.toHexString(),
            fileChecksum = "00000000000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
        )
    }

    private fun getCpkMetadata(): List<CpkSummary> {
        return sandboxCpks.filter { it.isContractCpk() }.map { cpk ->
            CpkSummary(
                name = cpk.cpkId.name,
                version = cpk.cpkId.version,
                signerSummaryHash = cpk.cpkId.signerSummaryHash?.toHexString() ?: "",
                fileChecksum = cpk.fileChecksum.toHexString()
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConsensualTransactionBuilderImpl) return false
        if (other.states.size != states.size) return false

        return other.states.withIndex().all {
            it.value == states[it.index]
        }
    }

    override fun hashCode(): Int = states.hashCode()

    private fun copy(states: List<ConsensualState> = this.states): ConsensualTransactionBuilderImpl {
        return ConsensualTransactionBuilderImpl(
            cipherSchemeMetadata,
            digestService,
            jsonMarshallingService,
            merkleTreeProvider,
            serializationService,
            signingService,
            memberLookup,
            sandboxCpks,
            states,
        )
    }
}
