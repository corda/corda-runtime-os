package net.corda.ledger.utxo.flow.impl

import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.utxo.flow.impl.flows.finality.UtxoFinalityFlow
import net.corda.ledger.utxo.flow.impl.flows.finality.UtxoReceiveFinalityFlow
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerStateQueryService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderImpl
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.ledger.utxo.flow.impl.transaction.filtered.UtxoFilteredTransactionBuilderImpl
import net.corda.ledger.utxo.flow.impl.transaction.filtered.factory.UtxoFilteredTransactionFactory
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
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

@Component(service = [UtxoLedgerService::class, UsedByFlow::class], scope = PROTOTYPE)
class UtxoLedgerServiceImpl @Activate constructor(
    @Reference(service = UtxoFilteredTransactionFactory::class)
    private val utxoFilteredTransactionFactory: UtxoFilteredTransactionFactory,
    @Reference(service = UtxoSignedTransactionFactory::class)
    private val utxoSignedTransactionFactory: UtxoSignedTransactionFactory,
    @Reference(service = FlowEngine::class)
    private val flowEngine: FlowEngine,
    @Reference(service = UtxoLedgerPersistenceService::class)
    private val utxoLedgerPersistenceService: UtxoLedgerPersistenceService,
    @Reference(service = UtxoLedgerStateQueryService::class)
    private val utxoLedgerStateQueryService: UtxoLedgerStateQueryService,
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = FlowSandboxService::class)
    private val flowSandboxGroupService: FlowSandboxService,
    @Reference(service = NotaryLookup::class)
    private val notaryLookup: NotaryLookup

) : UtxoLedgerService, UsedByFlow, SingletonSerializeAsToken {

    private companion object {
        val log = contextLogger()
    }


    @Suspendable
    override fun getTransactionBuilder(): UtxoTransactionBuilder =
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
        return UtxoFilteredTransactionBuilderImpl(utxoFilteredTransactionFactory, signedTransaction as UtxoSignedTransactionInternal)
    }

    @Suspendable
    override fun <T: ContractState> findUnconsumedStatesByType(stateClass: Class<out T>): List<StateAndRef<T>> {
        return utxoLedgerStateQueryService.findUnconsumedStatesByType(stateClass)
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

    private fun getPluggableNotaryClientFlow(notary: Party): Class<PluggableNotaryClientFlow> {

        val protocolName =
            notaryLookup.notaryServices.firstOrNull { it.name == notary.name }?.pluginClass ?: throw CordaRuntimeException(
                "Plugin class not found for notary service ${notary.name}" +
                        ". This means that no notary service matching this name has been registered " +
                        "on the network."
            )

        val sandboxGroupContext = currentSandboxGroupContext.get()

        val flowName = flowSandboxGroupService
            .get(sandboxGroupContext.virtualNodeContext.holdingIdentity)
            .protocolStore
            .initiatorForProtocol(protocolName, supportedVersions = listOf(1))

        log.info("Initiator for protocol $protocolName is: $flowName")

        @Suppress("UNCHECKED_CAST")
        return (sandboxGroupContext.sandboxGroup.loadClassFromMainBundles(flowName) as? Class<PluggableNotaryClientFlow>) ?: throw FlowFatalException(
            "Notary client flow class $flowName is invalid because does not inherit from " +
                    "${PluggableNotaryClientFlow::class.simpleName}."
        )
    }

    @Suspendable
    override fun receiveFinality(
        session: FlowSession,
        validator: UtxoTransactionValidator
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
}
