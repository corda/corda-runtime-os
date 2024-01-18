package net.corda.ledger.utxo.flow.impl

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.persistence.query.ResultSetFactory
import net.corda.flow.pipeline.sessions.protocol.FlowProtocolStore
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.utxo.flow.impl.flows.finality.UtxoFinalityFlow
import net.corda.ledger.utxo.flow.impl.flows.finality.UtxoReceiveFinalityFlow
import net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.ReceiveAndUpdateTransactionBuilderFlow
import net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.SendTransactionBuilderDiffFlow
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.ReceiveSignedTransactionFlow
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.ReceiveWireTransactionFlow
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.SendSignedTransactionFlow
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.SendWireTransactionFlow
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerStateQueryService
import net.corda.ledger.utxo.flow.impl.persistence.VaultNamedParameterizedQueryImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoBaselinedTransactionBuilder
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.ledger.utxo.flow.impl.transaction.filtered.UtxoFilteredTransactionBuilderImpl
import net.corda.ledger.utxo.flow.impl.transaction.filtered.factory.UtxoFilteredTransactionFactory
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.utilities.time.UTCClock
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.FinalizationResult
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.query.VaultNamedParameterizedQuery
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionValidator
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionBuilder
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction
import java.time.Instant

@Suppress("LongParameterList", "TooManyFunctions")
@Component(service = [UtxoLedgerService::class, UsedByFlow::class], scope = PROTOTYPE)
class UtxoLedgerServiceImpl @Activate constructor(
    @Reference(service = UtxoFilteredTransactionFactory::class) private val utxoFilteredTransactionFactory: UtxoFilteredTransactionFactory,
    @Reference(service = UtxoSignedTransactionFactory::class) private val utxoSignedTransactionFactory: UtxoSignedTransactionFactory,
    @Reference(service = FlowEngine::class) private val flowEngine: FlowEngine,
    @Reference(service = UtxoLedgerPersistenceService::class) private val utxoLedgerPersistenceService: UtxoLedgerPersistenceService,
    @Reference(service = UtxoLedgerStateQueryService::class) private val utxoLedgerStateQueryService: UtxoLedgerStateQueryService,
    @Reference(service = CurrentSandboxGroupContext::class) private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = NotaryLookup::class) private val notaryLookup: NotaryLookup,
    @Reference(service = ExternalEventExecutor::class) private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = ResultSetFactory::class) private val resultSetFactory: ResultSetFactory,
    @Reference(service = UtxoLedgerTransactionVerificationService::class)
    private val transactionVerificationService: UtxoLedgerTransactionVerificationService
) : UtxoLedgerService, UsedByFlow, SingletonSerializeAsToken {
    /* A service class which is typically injected in to any cordApp which wishes to interact with the UTXO ledger.
     * Therefore the methods of this class are typically running from within flow sandboxes, and are subject
     * to the limitations of flows. In particular since flows use Quasar every method that can be block must be annotated
     * @Suspendable, and since it is not possible to annotate lambdas they sometimes cannot be used.
     */
    private companion object {
        const val FIND_UNCONSUMED_STATES_BY_EXACT_TYPE = "CORDA_FIND_UNCONSUMED_STATES_BY_EXACT_TYPE"
        val clock = UTCClock()
    }

    @Suspendable
    override fun createTransactionBuilder() =
        UtxoTransactionBuilderImpl(utxoSignedTransactionFactory, notaryLookup)

    @Suspendable
    override fun verify(ledgerTransaction: UtxoLedgerTransaction) {
        transactionVerificationService.verify(ledgerTransaction)
    }

    @Suppress("UNCHECKED_CAST")
    @Suspendable
    override fun <T : ContractState> resolve(stateRefs: Iterable<StateRef>): List<StateAndRef<T>> {
        return utxoLedgerStateQueryService.resolveStateRefs(stateRefs) as List<StateAndRef<T>>
    }

    @Suspendable
    override fun <T : ContractState> resolve(stateRef: StateRef): StateAndRef<T> {
        return resolve<T>(listOf(stateRef)).first()
    }

    @Suspendable
    override fun findSignedTransaction(id: SecureHash): UtxoSignedTransaction? {
        return utxoLedgerPersistenceService.findSignedTransaction(id, TransactionStatus.VERIFIED)
    }

    @Suspendable
    override fun findLedgerTransaction(id: SecureHash): UtxoLedgerTransaction? {
        return utxoLedgerPersistenceService.findSignedLedgerTransaction(id)?.ledgerTransaction
    }

    @Suspendable
    override fun filterSignedTransaction(signedTransaction: UtxoSignedTransaction): UtxoFilteredTransactionBuilder {
        return UtxoFilteredTransactionBuilderImpl(
            utxoFilteredTransactionFactory,
            signedTransaction as UtxoSignedTransactionInternal
        )
    }

    @Suspendable
    @Deprecated("Deprecated in inherited API")
    @Suppress("deprecation", "removal")
    override fun <T : ContractState> findUnconsumedStatesByType(type: Class<T>): List<StateAndRef<T>> {
        return utxoLedgerStateQueryService.findUnconsumedStatesByType(type)
    }

    @Suspendable
    override fun <T : ContractState> findUnconsumedStatesByExactType(
        type: Class<T>,
        limit: Int,
        createdTimestampLimit: Instant
    ): PagedQuery.ResultSet<StateAndRef<T>> {
        @Suppress("UNCHECKED_CAST")
        return query(FIND_UNCONSUMED_STATES_BY_EXACT_TYPE, StateAndRef::class.java)
            .setParameter("type", type.name)
            .setCreatedTimestampLimit(createdTimestampLimit)
            .setLimit(limit)
            .execute() as PagedQuery.ResultSet<StateAndRef<T>>
    }

    @Suspendable
    override fun findFilteredTransactionsAndSignatures(
        signedTransaction: UtxoSignedTransaction
    ): Map<SecureHash, UtxoFilteredTransactionAndSignatures> {
        return utxoLedgerPersistenceService.findFilteredTransactionsAndSignatures(
            signedTransaction.inputStateRefs + signedTransaction.referenceStateRefs,
            signedTransaction.notaryKey,
            signedTransaction.notaryName
        )
    }

    @Suspendable
    override fun finalize(
        signedTransaction: UtxoSignedTransaction,
        sessions: List<FlowSession>
    ): FinalizationResult {
        /*
         * Called from user flows when it is time to verify, sign and distribute a transaction.
         *
         * `signedTransaction` has various bits of data for the transaction. It isn't actually signed at this point,
         * but does include a list of the public keys from key pairs that should be used to sign the transaction.
         *
         * `sessions` has one entry for each other virtual node that should receive the transaction and hopefully
         * sign it; they will hopefully call in via `receiveFinality`
         *
         */

        /*
        Need [doPrivileged] due to [contextLogger] being used in the flow's constructor.
        Creating the executing the SubFlow must be independent otherwise the security manager causes issues with Quasar.
         */
        val utxoFinalityFlow = try {
            @Suppress("deprecation", "removal")
            java.security.AccessController.doPrivileged(
                PrivilegedExceptionAction {
                    UtxoFinalityFlow(
                        signedTransaction as UtxoSignedTransactionInternal,
                        sessions,
                        getPluggableNotaryDetails(signedTransaction.notaryName)
                    )
                }
            )
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
        return FinalizationResultImpl(flowEngine.subFlow(utxoFinalityFlow))
    }

    @Suspendable
    override fun receiveFinality(
        session: FlowSession,
        validator: UtxoTransactionValidator
    ): FinalizationResult {
        /*
         * Called by flows in user corDapps that wish to participate in finality, to counter sign them. Works
         * by starting a new receive finality flow to do the work.
         *
         * `session` provides the ability to receive the transaction from the counterparty who initiated the finalize,
         * and later send back to the counterparty who initiated the finalize, as well as providing access to the
         * X500Name of the counterparty.
         */
        val utxoReceiveFinalityFlow = try {
            @Suppress("deprecation", "removal")
            java.security.AccessController.doPrivileged(
                PrivilegedExceptionAction {
                    UtxoReceiveFinalityFlow(session, validator)
                }
            )
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
        return FinalizationResultImpl(flowEngine.subFlow(utxoReceiveFinalityFlow))
    }

    @Suspendable
    override fun <R> query(queryName: String, resultClass: Class<R>): VaultNamedParameterizedQuery<R> {
        return VaultNamedParameterizedQueryImpl(
            queryName,
            externalEventExecutor,
            currentSandboxGroupContext,
            resultSetFactory,
            parameters = mutableMapOf(),
            limit = Int.MAX_VALUE,
            offset = 0,
            resultClass,
            clock
        )
    }

    @Suspendable
    override fun persistDraftSignedTransaction(utxoSignedTransaction: UtxoSignedTransaction) {
        // Verifications need to be at least as strong as what
        // net.corda.ledger.utxo.flow.impl.flows.finality.v1.UtxoFinalityFlowV1.call
        // does before the initial persist()

        utxoSignedTransaction as UtxoSignedTransactionInternal
        // verifyExistingSignatures
        check(utxoSignedTransaction.signatures.isNotEmpty()) {
            "Received draft transaction without signatures."
        }
        utxoSignedTransaction.signatures.forEach {
            utxoSignedTransaction.verifySignatorySignature(it)
        }
        // verifyTransaction
        transactionVerificationService.verify(utxoSignedTransaction.toLedgerTransaction())

        // Initial verifications passed, the transaction can be saved in the database.
        utxoLedgerPersistenceService.persist(utxoSignedTransaction, TransactionStatus.DRAFT)
    }

    @Suspendable
    override fun findDraftSignedTransaction(id: SecureHash): UtxoSignedTransaction? {
        return utxoLedgerPersistenceService.findSignedTransaction(id, TransactionStatus.DRAFT)
    }

    // Retrieve notary client plugin class for specified notary service identity. This is done in
    // a non-suspendable function to avoid trying (and failing) to serialize the objects used
    // internally.
    @VisibleForTesting
    @Suppress("ThrowsCount")
    internal fun getPluggableNotaryDetails(notary: MemberX500Name): PluggableNotaryDetails {
        val notaryInfo = notaryLookup.notaryServices.firstOrNull { it.name == notary }
            ?: throw CordaRuntimeException(
                "Notary service $notary has not been registered on the network."
            )

        val sandboxGroupContext = currentSandboxGroupContext.get()

        val protocolStore =
            sandboxGroupContext.getObjectByKey<FlowProtocolStore>("FLOW_PROTOCOL_STORE") ?: throw CordaRuntimeException(
                "Cannot get flow protocol store for current sandbox group context"
            )

        val flowName = protocolStore.initiatorForProtocol(notaryInfo.protocol, notaryInfo.protocolVersions)

        val flowClass = sandboxGroupContext.sandboxGroup.loadClassFromMainBundles(flowName)

        if (!PluggableNotaryClientFlow::class.java.isAssignableFrom(flowClass)) {
            throw CordaRuntimeException(
                "Notary client flow class $flowName is invalid because " +
                    "it does not inherit from ${PluggableNotaryClientFlow::class.simpleName}."
            )
        }

        @Suppress("UNCHECKED_CAST")
        return PluggableNotaryDetails(
            flowClass as Class<PluggableNotaryClientFlow>,
            notaryInfo.isBackchainRequired
        )
    }

    @Suspendable
    override fun receiveTransactionBuilder(session: FlowSession): UtxoTransactionBuilder {
        val receivedTransactionBuilder = flowEngine.subFlow(
            ReceiveAndUpdateTransactionBuilderFlow(
                session,
                createTransactionBuilder()
            )
        )
        return UtxoBaselinedTransactionBuilder(
            receivedTransactionBuilder as UtxoTransactionBuilderInternal
        )
    }

    @Suspendable
    override fun receiveTransaction(session: FlowSession): UtxoSignedTransaction {
        return flowEngine.subFlow(ReceiveSignedTransactionFlow(session))
    }

    @Suspendable
    override fun sendTransaction(signedTransaction: UtxoSignedTransaction, sessions: List<FlowSession>) {
        flowEngine.subFlow(
            SendSignedTransactionFlow(
                signedTransaction,
                sessions,
                forceBackchainResolution = false
            )
        )
    }

    @Suspendable
    override fun sendTransactionWithBackchain(
        signedTransaction: UtxoSignedTransaction,
        sessions: MutableList<FlowSession>
    ) {
        flowEngine.subFlow(
            SendSignedTransactionFlow(
                signedTransaction,
                sessions,
                forceBackchainResolution = true
            )
        )
    }

    @Suspendable
    override fun receiveLedgerTransaction(session: FlowSession): UtxoLedgerTransaction {
        return flowEngine.subFlow(ReceiveWireTransactionFlow(session))
    }

    @Suspendable
    override fun sendLedgerTransaction(signedTransaction: UtxoSignedTransaction, sessions: List<FlowSession>) {
        flowEngine.subFlow(SendWireTransactionFlow(signedTransaction, sessions))
    }

    @Suspendable
    override fun sendUpdatedTransactionBuilder(
        transactionBuilder: UtxoTransactionBuilder,
        session: FlowSession,
    ) {
        if (transactionBuilder !is UtxoBaselinedTransactionBuilder) {
            throw UnsupportedOperationException("Only received transaction builder proposals can be used in replies.")
        }
        flowEngine.subFlow(
            SendTransactionBuilderDiffFlow(
                transactionBuilder,
                session
            )
        )
    }

    @Suspendable
    override fun sendAndReceiveTransactionBuilder(
        transactionBuilder: UtxoTransactionBuilder,
        session: FlowSession
    ): UtxoTransactionBuilder {
        flowEngine.subFlow(
            SendTransactionBuilderDiffFlow(
                transactionBuilder as UtxoTransactionBuilderInternal,
                session
            )
        )
        return flowEngine.subFlow(
            ReceiveAndUpdateTransactionBuilderFlow(
                session,
                transactionBuilder
            )
        )
    }
}
