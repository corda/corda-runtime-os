package net.corda.ledger.persistence.utxo.impl

import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedGroupParameters
import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.persistence.common.mapToComponentGroups
import net.corda.ledger.persistence.utxo.CustomRepresentation
import net.corda.ledger.persistence.utxo.UtxoRepository
import net.corda.ledger.utxo.data.transaction.UtxoTransactionOutputDto
import net.corda.sandbox.type.SandboxConstants.CORDA_MARKER_ONLY_SERVICE
import net.corda.sandbox.type.UsedByPersistence
import net.corda.utilities.debug
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.StateRef
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.Query
import javax.persistence.Tuple

@Suppress("TooManyFunctions")
/**
 * Reads and writes ledger transaction data to and from the virtual node vault database.
 * The component only exists to be created inside a PERSISTENCE sandbox. We denote it
 * as "corda.marker.only" to force the sandbox to create it, despite it implementing
 * only the [UsedByPersistence] marker interface.
 */
@Component(
    service = [ UtxoRepository::class, UsedByPersistence::class ],
    property = [ CORDA_MARKER_ONLY_SERVICE ],
    scope = PROTOTYPE
)
class UtxoRepositoryImpl @Activate constructor(
    @Reference
    private val serializationService: SerializationService,
    @Reference
    private val wireTransactionFactory: WireTransactionFactory,
    @Reference
    private val queryProvider: UtxoQueryProvider
) : UtxoRepository, UsedByPersistence {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun findTransaction(
        entityManager: EntityManager,
        id: String
    ): SignedTransactionContainer? {
        val privacySalt = findTransactionPrivacySalt(entityManager, id) ?: return null
        val wireTransaction = wireTransactionFactory.create(
            findTransactionComponentLeafs(entityManager, id),
            privacySalt
        )
        return SignedTransactionContainer(
            wireTransaction,
            findTransactionSignatures(entityManager, id)
        )
    }

    private fun findTransactionPrivacySalt(
        entityManager: EntityManager,
        transactionId: String
    ): PrivacySaltImpl? {
        return entityManager.createNativeQuery(queryProvider.findTransactionPrivacySalt, Tuple::class.java)
            .setParameter("transactionId", transactionId)
            .resultListAsTuples()
            .map { r -> PrivacySaltImpl(r.get(0) as ByteArray) }
            .firstOrNull()
    }

    override fun findTransactionComponentLeafs(
        entityManager: EntityManager,
        transactionId: String
    ): Map<Int, List<ByteArray>> {
        return entityManager.createNativeQuery(queryProvider.findTransactionComponentLeafs, Tuple::class.java)
            .setParameter("transactionId", transactionId)
            .resultListAsTuples()
            .mapToComponentGroups(UtxoComponentGroupMapper(transactionId))
    }

    override fun findUnconsumedVisibleStatesByType(
        entityManager: EntityManager
    ): List<UtxoTransactionOutputDto> {
        return entityManager.createNativeQuery(queryProvider.findUnconsumedVisibleStatesByType, Tuple::class.java)
            .setParameter("verified", TransactionStatus.VERIFIED.value)
            .resultListAsTuples()
            .map { t ->
                UtxoTransactionOutputDto(
                    t[0] as String, // transactionId
                    t[1] as Int, // leaf ID
                    t[2] as ByteArray, // outputs info data
                    t[3] as ByteArray // outputs data
                )
            }
    }

    override fun resolveStateRefs(
        entityManager: EntityManager,
        stateRefs: List<StateRef>
    ): List<UtxoTransactionOutputDto> {
        return entityManager.createNativeQuery(queryProvider.resolveStateRefs, Tuple::class.java)
            .setParameter("transactionIds", stateRefs.map { it.transactionId.toString() })
            .setParameter("stateRefs", stateRefs.map { it.toString() })
            .setParameter("verified", TransactionStatus.VERIFIED.value)
            .resultListAsTuples()
            .map { t ->
                UtxoTransactionOutputDto(
                    t[0] as String, // transactionId
                    t[1] as Int, // leaf ID
                    t[2] as ByteArray, // outputs info data
                    t[3] as ByteArray // outputs data
                )
            }
    }

    override fun findTransactionSignatures(
        entityManager: EntityManager,
        transactionId: String
    ): List<DigitalSignatureAndMetadata> {
        return entityManager.createNativeQuery(queryProvider.findTransactionSignatures, Tuple::class.java)
            .setParameter("transactionId", transactionId)
            .resultListAsTuples()
            .map { r -> serializationService.deserialize(r.get(0) as ByteArray) }
    }

    override fun findTransactionStatus(entityManager: EntityManager, id: String): String? {
        return entityManager.createNativeQuery(queryProvider.findTransactionStatus, Tuple::class.java)
            .setParameter("transactionId", id)
            .resultListAsTuples()
            .map { r -> r.get(0) as String }
            .singleOrNull()
    }

    override fun markTransactionVisibleStatesConsumed(
        entityManager: EntityManager,
        stateRefs: List<StateRef>,
        timestamp: Instant
    ) {
        entityManager.createNativeQuery(queryProvider.markTransactionVisibleStatesConsumed)
            .setParameter("consumed", timestamp)
            .setParameter("transactionIds", stateRefs.map { it.transactionId.toString() })
            .setParameter("stateRefs", stateRefs.map(StateRef::toString))
            .executeUpdate()
    }

    override fun persistTransaction(
        entityManager: EntityManager,
        id: String,
        privacySalt: ByteArray,
        account: String,
        timestamp: Instant
    ) {
        entityManager.createNativeQuery(queryProvider.persistTransaction)
            .setParameter("id", id)
            .setParameter("privacySalt", privacySalt)
            .setParameter("accountId", account)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
            .logResult("transaction [$id]")
    }

    override fun persistTransactionComponentLeaf(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        data: ByteArray,
        hash: String,
        timestamp: Instant
    ) {
        entityManager.createNativeQuery(queryProvider.persistTransactionComponentLeaf)
            .setParameter("transactionId", transactionId)
            .setParameter("groupIndex", groupIndex)
            .setParameter("leafIndex", leafIndex)
            .setParameter("data", data)
            .setParameter("hash", hash)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
            .logResult("transaction component [$transactionId, $groupIndex, $leafIndex]")
    }

    override fun persistTransactionCpk(
        entityManager: EntityManager,
        transactionId: String,
        fileChecksums: Collection<String>
    ) {
        entityManager.createNativeQuery(queryProvider.persistTransactionCpk)
            .setParameter("transactionId", transactionId)
            .setParameter("fileChecksums", fileChecksums)
            .executeUpdate()
            .logResult("transaction CPK [$transactionId, $fileChecksums]")
    }

    override fun persistTransactionOutput(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        type: String,
        tokenType: String?,
        tokenIssuerHash: String?,
        tokenSymbol: String?,
        tokenTag: String?,
        tokenOwnerHash: String?,
        tokenAmount: BigDecimal?,
        timestamp: Instant
    ) {
        entityManager.createNativeQuery(queryProvider.persistTransactionOutput)
            .setParameter("transactionId", transactionId)
            .setParameter("groupIndex", groupIndex)
            .setParameter("leafIndex", leafIndex)
            .setParameter("type", type)
            .setParameter("tokenType", tokenType)
            .setParameter("tokenIssuerHash", tokenIssuerHash)
            .setParameter("tokenSymbol", tokenSymbol)
            .setParameter("tokenTag", tokenTag)
            .setParameter("tokenOwnerHash", tokenOwnerHash)
            // This is a workaround for avoiding error when tokenAmount is null, see:
            // https://stackoverflow.com/questions/53648865/postgresql-spring-data-jpa-integer-null-interpreted-as-bytea
            .setParameter("tokenAmount", BigDecimal.ZERO)
            .setParameter("tokenAmount", tokenAmount)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
            .logResult("transaction output [$transactionId, $groupIndex, $leafIndex]")
    }

    override fun persistTransactionVisibleStates(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        consumed: Boolean,
        customRepresentation: CustomRepresentation,
        timestamp: Instant,
    ) {
        entityManager.createNativeQuery(queryProvider.persistTransactionVisibleStates(consumed))
            .setParameter("transactionId", transactionId)
            .setParameter("groupIndex", groupIndex)
            .setParameter("leafIndex", leafIndex)
            .setParameter("custom_representation", customRepresentation.json)
            .setParameter("createdAt", timestamp)
            .run { if (consumed) setParameter("consumedAt", timestamp) else this }
            .executeUpdate()
            .logResult("transaction relevancy [$transactionId, $groupIndex, $leafIndex]")
    }

    override fun persistTransactionSignature(
        entityManager: EntityManager,
        transactionId: String,
        index: Int,
        signature: DigitalSignatureAndMetadata,
        timestamp: Instant
    ) {
        entityManager.createNativeQuery(queryProvider.persistTransactionSignature)
            .setParameter("transactionId", transactionId)
            .setParameter("signatureIdx", index)
            .setParameter("signature", serializationService.serialize(signature).bytes)
            .setParameter("publicKeyHash", signature.by.toString())
            .setParameter("createdAt", timestamp)
            .executeUpdate()
            .logResult("transaction signature [$transactionId, $index]")
    }

    override fun persistTransactionSource(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        refTransactionId: String,
        refLeafIndex: Int,
        isRefInput: Boolean,
        timestamp: Instant
    ) {
        entityManager.createNativeQuery(queryProvider.persistTransactionSource)
            .setParameter("transactionId", transactionId)
            .setParameter("groupIndex", groupIndex)
            .setParameter("leafIndex", leafIndex)
            .setParameter("refTransactionId", refTransactionId)
            .setParameter("refLeafIndex", refLeafIndex)
            .setParameter("isRefInput", isRefInput)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
            .logResult("transaction source [$transactionId, $groupIndex, $leafIndex]")
    }

    override fun persistTransactionStatus(
        entityManager: EntityManager,
        transactionId: String,
        transactionStatus: TransactionStatus,
        timestamp: Instant
    ) {
        // Insert/update status. Update ignored unless: UNVERIFIED -> * | VERIFIED -> VERIFIED | INVALID -> INVALID
        val rowsUpdated = entityManager.createNativeQuery(queryProvider.persistTransactionStatus)
            .setParameter("transactionId", transactionId)
            .setParameter("status", transactionStatus.value)
            .setParameter("updatedAt", timestamp)
            .executeUpdate()
            .logResult("transaction status [$transactionId, ${transactionStatus.value}]")

        check(rowsUpdated == 1 || transactionStatus == TransactionStatus.UNVERIFIED) {
            // VERIFIED -> INVALID or INVALID -> VERIFIED is a system error as verify should always be consistent and deterministic
            "Existing status for transaction with ID $transactionId can't be updated to $transactionStatus"
        }
    }

    override fun findSignedGroupParameters(entityManager: EntityManager, hash: String): SignedGroupParameters? {
        return entityManager.createNativeQuery(queryProvider.findSignedGroupParameters, Tuple::class.java)
            .setParameter("hash", hash)
            .resultListAsTuples()
            .map { r ->
                SignedGroupParameters(
                    ByteBuffer.wrap(r.get(0) as ByteArray),
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap(r.get(1) as ByteArray),
                        ByteBuffer.wrap(r.get(2) as ByteArray)
                    ),
                    CryptoSignatureSpec((r.get(3) as String), null, null)
                )
            }
            .singleOrNull()
    }

    override fun persistSignedGroupParameters(
        entityManager: EntityManager,
        hash: String,
        signedGroupParameters: SignedGroupParameters,
        timestamp: Instant
    ) {
        entityManager.createNativeQuery(queryProvider.persistSignedGroupParameters)
            .setParameter("hash", hash)
            .setParameter("parameters", signedGroupParameters.groupParameters.array())
            .setParameter("signature_public_key", signedGroupParameters.mgmSignature.publicKey.array())
            .setParameter("signature_content", signedGroupParameters.mgmSignature.bytes.array())
            .setParameter("signature_spec", signedGroupParameters.mgmSignatureSpec.signatureName)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
            .logResult("signed group parameters [$hash]")
    }

    private fun Int.logResult(entity: String): Int {
        if (this == 0) {
            logger.debug {
                "UTXO ledger entity not persisted due to existing row in database. Entity: $entity"
            }
        }
        return this
    }

    @Suppress("UNCHECKED_CAST")
    private fun Query.resultListAsTuples() = resultList as List<Tuple>
}
