package net.corda.ledger.utxo.flow.impl.persistence

import io.micrometer.core.instrument.Timer
import net.corda.crypto.core.fullIdHash
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.metrics.recordSuspendable
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.TransactionStatus.Companion.toTransactionStatus
import net.corda.ledger.common.data.transaction.filtered.FilteredTransaction
import net.corda.ledger.utxo.data.transaction.SignedLedgerTransactionContainer
import net.corda.ledger.utxo.flow.impl.cache.StateAndRefCache
import net.corda.ledger.utxo.flow.impl.persistence.LedgerPersistenceMetricOperationName.FindFilteredTransactionsAndSignatures
import net.corda.ledger.utxo.flow.impl.persistence.LedgerPersistenceMetricOperationName.FindSignedLedgerTransactionWithStatus
import net.corda.ledger.utxo.flow.impl.persistence.LedgerPersistenceMetricOperationName.FindTransactionIdsAndStatuses
import net.corda.ledger.utxo.flow.impl.persistence.LedgerPersistenceMetricOperationName.FindTransactionWithStatus
import net.corda.ledger.utxo.flow.impl.persistence.LedgerPersistenceMetricOperationName.PersistTransaction
import net.corda.ledger.utxo.flow.impl.persistence.LedgerPersistenceMetricOperationName.PersistTransactionIfDoesNotExist
import net.corda.ledger.utxo.flow.impl.persistence.LedgerPersistenceMetricOperationName.UpdateTransactionStatus
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindFilteredTransactionsAndSignaturesExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindFilteredTransactionsAndSignaturesParameters
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindSignedLedgerTransactionExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindSignedLedgerTransactionParameters
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindTransactionExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindTransactionIdsAndStatusesExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindTransactionIdsAndStatusesParameters
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindTransactionParameters
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistTransactionExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistTransactionIfDoesNotExistExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistTransactionIfDoesNotExistParameters
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistTransactionParameters
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistTransactionSignaturesExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistTransactionSignaturesParameters
import net.corda.ledger.utxo.flow.impl.persistence.external.events.UpdateTransactionStatusExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.UpdateTransactionStatusParameters
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedLedgerTransaction
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedLedgerTransactionImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.ledger.utxo.flow.impl.transaction.filtered.factory.UtxoFilteredTransactionFactory
import net.corda.metrics.CordaMetrics
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.nio.ByteBuffer
import java.security.PublicKey

@Suppress("LongParameterList", "TooManyFunctions")
@Component(
    service = [UtxoLedgerPersistenceService::class, UsedByFlow::class],
    property = [CORDA_SYSTEM_SERVICE],
    scope = PROTOTYPE
)
class UtxoLedgerPersistenceServiceImpl @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = UtxoLedgerTransactionFactory::class)
    private val utxoLedgerTransactionFactory: UtxoLedgerTransactionFactory,
    @Reference(service = UtxoSignedTransactionFactory::class)
    private val utxoSignedTransactionFactory: UtxoSignedTransactionFactory,
    @Reference(service = UtxoFilteredTransactionFactory::class)
    private val utxoFilteredTransactionFactory: UtxoFilteredTransactionFactory,
    @Reference(service = NotarySignatureVerificationService::class)
    private val notarySignatureVerificationService: NotarySignatureVerificationService,
    @Reference(service = StateAndRefCache::class)
    private val stateAndRefCache: StateAndRefCache
) : UtxoLedgerPersistenceService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun findSignedTransaction(id: SecureHash, transactionStatus: TransactionStatus): UtxoSignedTransaction? {
        return findSignedTransactionWithStatus(id, transactionStatus)?.first
    }

    @Suspendable
    override fun findSignedTransactionWithStatus(
        id: SecureHash,
        transactionStatus: TransactionStatus
    ): Pair<UtxoSignedTransaction?, TransactionStatus>? {
        return recordSuspendable({ ledgerPersistenceFlowTimer(FindTransactionWithStatus) }) @Suspendable {
            wrapWithPersistenceException {
                externalEventExecutor.execute(
                    FindTransactionExternalEventFactory::class.java,
                    FindTransactionParameters(id.toString(), transactionStatus)
                )
            }.firstOrNull()?.let {
                val (transaction, status) = serializationService.deserialize<Pair<SignedTransactionContainer?, String?>>(it.array())
                if (status == null) {
                    return@let null
                }
                transaction?.toSignedTransaction() to status.toTransactionStatus()
            }
        }
    }

    @Suspendable
    override fun findTransactionIdsAndStatuses(ids: Collection<SecureHash>): Map<SecureHash, TransactionStatus> {
        return recordSuspendable({ ledgerPersistenceFlowTimer(FindTransactionIdsAndStatuses) }) @Suspendable {
            wrapWithPersistenceException {
                externalEventExecutor.execute(
                    FindTransactionIdsAndStatusesExternalEventFactory::class.java,
                    FindTransactionIdsAndStatusesParameters(
                        ids.map { it.toString() }
                    )
                )
            }
        }.firstOrNull()?.let {
            serializationService.deserialize<Map<SecureHash, String>>(it.array())
                .mapValues { (_, status) -> status.toTransactionStatus() }
        } ?: emptyMap()
    }

    @Suspendable
    override fun findSignedLedgerTransaction(id: SecureHash): UtxoSignedLedgerTransaction? {
        return findSignedLedgerTransactionWithStatus(id, TransactionStatus.VERIFIED)?.first
    }

    @Suspendable
    override fun findSignedLedgerTransactionWithStatus(
        id: SecureHash,
        transactionStatus: TransactionStatus
    ): Pair<UtxoSignedLedgerTransaction?, TransactionStatus>? {
        return recordSuspendable({ ledgerPersistenceFlowTimer(FindSignedLedgerTransactionWithStatus) }) @Suspendable {
            wrapWithPersistenceException {
                externalEventExecutor.execute(
                    FindSignedLedgerTransactionExternalEventFactory::class.java,
                    FindSignedLedgerTransactionParameters(id.toString(), transactionStatus)
                )
            }.firstOrNull()?.let {
                val (transaction, status) = serializationService.deserialize<Pair<SignedLedgerTransactionContainer?, String?>>(it.array())
                if (status == null) {
                    return@let null
                }

                val signedLedgerTransaction = transaction?.toSignedLedgerTransaction()

                // Add resolved input state and refs to the cache, so it can be used in other places
                signedLedgerTransaction?.inputStateAndRefs?.let { inputStateAndRefs ->
                    stateAndRefCache.putAll(inputStateAndRefs)
                }

                // Add resolved reference state and refs to the cache, so it can be used in other places
                signedLedgerTransaction?.referenceStateAndRefs?.let { referenceStateAndRefs ->
                    stateAndRefCache.putAll(referenceStateAndRefs)
                }

                signedLedgerTransaction to status.toTransactionStatus()
            }
        }
    }

    @Suspendable
    override fun findFilteredTransactionsAndSignatures(
        stateRefs: List<StateRef>,
        notaryKey: PublicKey,
        notaryName: MemberX500Name
    ): Map<SecureHash, Map<UtxoFilteredTransaction, List<DigitalSignatureAndMetadata>>> {
        return recordSuspendable({ ledgerPersistenceFlowTimer(FindFilteredTransactionsAndSignatures) }) @Suspendable {
            wrapWithPersistenceException {
                externalEventExecutor.execute(
                    FindFilteredTransactionsAndSignaturesExternalEventFactory::class.java,
                    FindFilteredTransactionsAndSignaturesParameters(stateRefs)
                )
            }.firstOrNull()?.let {
                serializationService.deserialize<Map<SecureHash, Pair<FilteredTransaction?, List<DigitalSignatureAndMetadata>>>>(
                    it.array()
                ).mapValues { (transactionId, filteredTransactionAndSignature) ->
                    val (filteredTransaction, signatures) = filteredTransactionAndSignature

                    requireNotNull(filteredTransaction) {
                        "Dependent transaction $transactionId does not exist"
                    }

                    val utxoFilteredTransaction = utxoFilteredTransactionFactory.create(filteredTransaction)
                    notarySignatureVerificationService.verifyNotarySignatures(
                        utxoFilteredTransaction,
                        notaryKey,
                        signatures,
                        mutableMapOf()
                    )

                    require(notaryName == utxoFilteredTransaction.notaryName) {
                        "Notary name of filtered transaction \"${utxoFilteredTransaction.notaryName}\" doesn't match with " +
                            "notary service of current transaction \"${notaryName}\""
                    }

                    val newTxNotaryKeyIds: Set<SecureHash> = if (notaryKey is CompositeKey) {
                        // get public keys that is part of this composite key from leafKeys then key id from each public key
                        notaryKey.leafKeys.map { publicKey -> publicKey.fullIdHash() }.toSet()
                    } else {
                        setOf(notaryKey.fullIdHash())
                    }

                    mapOf(
                        utxoFilteredTransaction to signatures.filter { signature -> newTxNotaryKeyIds.contains(signature.by) }
                    )
                }
            }
        } ?: emptyMap()
    }

    @Suspendable
    override fun persist(
        transaction: UtxoSignedTransaction,
        transactionStatus: TransactionStatus,
        visibleStatesIndexes: List<Int>
    ): List<CordaPackageSummary> {
        return recordSuspendable({ ledgerPersistenceFlowTimer(PersistTransaction) }) @Suspendable {
            wrapWithPersistenceException {
                externalEventExecutor.execute(
                    PersistTransactionExternalEventFactory::class.java,
                    PersistTransactionParameters(serialize(transaction.toContainer()), transactionStatus, visibleStatesIndexes)
                )
            }.map { serializationService.deserialize(it.array()) }
        }
    }

    @Suspendable
    override fun updateStatus(id: SecureHash, transactionStatus: TransactionStatus) {
        recordSuspendable({ ledgerPersistenceFlowTimer(UpdateTransactionStatus) }) @Suspendable {
            wrapWithPersistenceException {
                externalEventExecutor.execute(
                    UpdateTransactionStatusExternalEventFactory::class.java,
                    UpdateTransactionStatusParameters(id.toString(), transactionStatus)
                )
            }
        }
    }

    @Suspendable
    override fun persistIfDoesNotExist(
        transaction: UtxoSignedTransaction,
        transactionStatus: TransactionStatus
    ): Pair<TransactionExistenceStatus, List<CordaPackageSummary>> {
        return recordSuspendable({ ledgerPersistenceFlowTimer(PersistTransactionIfDoesNotExist) }) @Suspendable {
            wrapWithPersistenceException {
                externalEventExecutor.execute(
                    PersistTransactionIfDoesNotExistExternalEventFactory::class.java,
                    PersistTransactionIfDoesNotExistParameters(serialize(transaction.toContainer()), transactionStatus)
                )
            }.first().let {
                val (status, summaries) = serializationService.deserialize<Pair<String?, List<CordaPackageSummary>>>(it.array())
                when (status) {
                    null -> TransactionExistenceStatus.DOES_NOT_EXIST
                    "U" -> TransactionExistenceStatus.UNVERIFIED
                    "V" -> TransactionExistenceStatus.VERIFIED
                    else -> throw IllegalStateException("Invalid status $status")
                } to summaries
            }
        }
    }

    @Suspendable
    override fun persistTransactionSignatures(id: SecureHash, startingIndex: Int, signatures: List<DigitalSignatureAndMetadata>) {
        return recordSuspendable(
            { ledgerPersistenceFlowTimer(LedgerPersistenceMetricOperationName.PersistTransactionSignatures) }
        ) @Suspendable {
            wrapWithPersistenceException {
                externalEventExecutor.execute(
                    PersistTransactionSignaturesExternalEventFactory::class.java,
                    PersistTransactionSignaturesParameters(
                        id.toString(),
                        startingIndex,
                        signatures.map { serializationService.serialize(it).bytes }
                    )
                )
            }
        }
    }

    private fun SignedTransactionContainer.toSignedTransaction(): UtxoSignedTransaction {
        return utxoSignedTransactionFactory.create(wireTransaction, signatures)
    }

    private fun UtxoSignedTransaction.toContainer(): SignedTransactionContainer {
        return (this as UtxoSignedTransactionInternal).run {
            SignedTransactionContainer(wireTransaction, signatures)
        }
    }

    private fun SignedLedgerTransactionContainer.toSignedLedgerTransaction(): UtxoSignedLedgerTransaction {
        return UtxoSignedLedgerTransactionImpl(
            utxoLedgerTransactionFactory.create(
                wireTransaction,
                serializedInputStateAndRefs,
                serializedReferenceStateAndRefs
            ),
            utxoSignedTransactionFactory.create(wireTransaction, signatures)
        )
    }

    private fun serialize(payload: Any) = ByteBuffer.wrap(serializationService.serialize(payload).bytes)

    private fun ledgerPersistenceFlowTimer(operationName: LedgerPersistenceMetricOperationName): Timer {
        return CordaMetrics.Metric.Ledger.PersistenceFlowTime
            .builder()
            .forVirtualNode(currentSandboxGroupContext.get().virtualNodeContext.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.OperationName, operationName.name)
            .build()
    }
}
