package net.corda.ledger.utxo.flow.impl

import net.corda.flow.pipeline.sessions.protocol.FlowProtocolStore
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.utxo.flow.impl.flows.finality.UtxoFinalityFlow
import net.corda.ledger.utxo.flow.impl.flows.finality.UtxoReceiveFinalityFlow
import net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.ReceiveAndUpdateTransactionBuilderFlow
import net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.SendTransactionBuilderDiffFlow
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerStateQueryService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoBaselinedTransactionBuilder
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.ledger.utxo.flow.impl.transaction.filtered.UtxoFilteredTransactionBuilderImpl
import net.corda.ledger.utxo.flow.impl.transaction.filtered.factory.UtxoFilteredTransactionFactory
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionValidator
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionBuilder
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.security.AccessController
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction

@Suppress("LongParameterList", "TooManyFunctions")
@Component(service = [UtxoLedgerService::class, UsedByFlow::class], scope = PROTOTYPE)
class UtxoLedgerServiceImpl @Activate constructor(
    @Reference(service = UtxoFilteredTransactionFactory::class) private val utxoFilteredTransactionFactory: UtxoFilteredTransactionFactory,
    @Reference(service = UtxoSignedTransactionFactory::class) private val utxoSignedTransactionFactory: UtxoSignedTransactionFactory,
    @Reference(service = FlowEngine::class) private val flowEngine: FlowEngine,
    @Reference(service = UtxoLedgerPersistenceService::class) private val utxoLedgerPersistenceService: UtxoLedgerPersistenceService,
    @Reference(service = UtxoLedgerStateQueryService::class) private val utxoLedgerStateQueryService: UtxoLedgerStateQueryService,
    @Reference(service = CurrentSandboxGroupContext::class) private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = NotaryLookup::class) private val notaryLookup: NotaryLookup

) : UtxoLedgerService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun getTransactionBuilder() =
        UtxoTransactionBuilderImpl(utxoSignedTransactionFactory)

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
        return utxoLedgerPersistenceService.find(id, TransactionStatus.VERIFIED)
    }

    @Suspendable
    override fun findLedgerTransaction(id: SecureHash): UtxoLedgerTransaction? {
        return utxoLedgerPersistenceService.find(id)?.toLedgerTransaction()
    }

    @Suspendable
    override fun filterSignedTransaction(signedTransaction: UtxoSignedTransaction): UtxoFilteredTransactionBuilder {
        return UtxoFilteredTransactionBuilderImpl(
            utxoFilteredTransactionFactory, signedTransaction as UtxoSignedTransactionInternal
        )
    }

    @Suspendable
    override fun <T : ContractState> findUnconsumedStatesByType(type: Class<T>): List<StateAndRef<T>> {
        return utxoLedgerStateQueryService.findUnconsumedStatesByType(type)
    }

    @Suspendable
    override fun finalize(
        signedTransaction: UtxoSignedTransaction,
        sessions: List<FlowSession>
    ): UtxoSignedTransaction {
        /*
        Need [doPrivileged] due to [contextLogger] being used in the flow's constructor.
        Creating the executing the SubFlow must be independent otherwise the security manager causes issues with Quasar.
        */
        val utxoFinalityFlow = try {
            AccessController.doPrivileged(PrivilegedExceptionAction {
                UtxoFinalityFlow(
                    signedTransaction as UtxoSignedTransactionInternal,
                    sessions,
                    getPluggableNotaryClientFlow(signedTransaction.notary)
                )
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
        return flowEngine.subFlow(utxoFinalityFlow)
    }

    @Suspendable
    override fun receiveFinality(
        session: FlowSession, validator: UtxoTransactionValidator
    ): UtxoSignedTransaction {
        val utxoReceiveFinalityFlow = try {
            AccessController.doPrivileged(PrivilegedExceptionAction {
                UtxoReceiveFinalityFlow(session, validator)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
        return flowEngine.subFlow(utxoReceiveFinalityFlow)
    }

    // Retrieve notary client plugin class for specified notary service identity. This is done in
    // a non-suspendable function to avoid trying (and failing) to serialize the objects used
    // internally.
    @VisibleForTesting
    @Suppress("ThrowsCount")
    internal fun getPluggableNotaryClientFlow(notary: Party): Class<PluggableNotaryClientFlow> {

        val protocolName = notaryLookup.notaryServices.firstOrNull { it.name == notary.name }?.pluginClass
            ?: throw CordaRuntimeException(
                "Plugin class not found for notary service " +
                        "${notary.name} . This means that no notary service matching this name " +
                        "has been registered on the network."
            )

        val sandboxGroupContext = currentSandboxGroupContext.get()

        val protocolStore =
            sandboxGroupContext.getObjectByKey<FlowProtocolStore>("FLOW_PROTOCOL_STORE") ?: throw CordaRuntimeException(
                "Cannot get flow protocol store for current sandbox group context"
            )

        // Hard-code supportedVersions to 1 for now, need MGM change to supply this, at which point
        // we can pass in (see CORE-9740)
        val flowName = protocolStore.initiatorForProtocol(protocolName, supportedVersions = listOf(1))

        val flowClass = sandboxGroupContext.sandboxGroup.loadClassFromMainBundles(flowName)

        if (!PluggableNotaryClientFlow::class.java.isAssignableFrom(flowClass)) {
            throw CordaRuntimeException(
                "Notary client flow class $flowName is invalid because " +
                        "it does not inherit from ${PluggableNotaryClientFlow::class.simpleName}."
            )
        }

        @Suppress("UNCHECKED_CAST") return flowClass as Class<PluggableNotaryClientFlow>
    }

    @Suspendable
    override fun receiveTransactionBuilder(session: FlowSession): UtxoTransactionBuilder {
        val receivedTransactionBuilder = flowEngine.subFlow(
            ReceiveAndUpdateTransactionBuilderFlow(
                session,
                getTransactionBuilder()
            )
        )
        return UtxoBaselinedTransactionBuilder(
            utxoSignedTransactionFactory,
            receivedTransactionBuilder as UtxoTransactionBuilderInternal
        )
    }

    @Suspendable
    override fun replyTransactionBuilderProposal(
        transactionBuilder: UtxoTransactionBuilder,
        session: FlowSession,
    ) {
        if (transactionBuilder !is UtxoBaselinedTransactionBuilder) {
            throw UnsupportedOperationException("Only received transaction builder proposals can be used in replies.")
        }
        flowEngine.subFlow(
            SendTransactionBuilderDiffFlow(
                transactionBuilder,
                session,
                transactionBuilder.baselineTransactionBuilder
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
                session,
                getTransactionBuilder()
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
