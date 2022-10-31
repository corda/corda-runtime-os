package net.corda.ledger.consensual.flow.impl

import net.corda.ledger.consensual.flow.impl.flows.finality.ConsensualFinalityFlow
import net.corda.ledger.consensual.flow.impl.flows.finality.ConsensualReceiveFinalityFlow
import net.corda.ledger.consensual.flow.impl.persistence.ConsensualLedgerPersistenceService
import net.corda.ledger.consensual.flow.impl.transaction.factory.ConsensualTransactionBuilderFactory
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransactionVerifier
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.security.AccessController
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction

@Component(service = [ConsensualLedgerService::class, SingletonSerializeAsToken::class], scope = PROTOTYPE)
class ConsensualLedgerServiceImpl @Activate constructor(
    @Reference(service = ConsensualTransactionBuilderFactory::class)
    private val consensualTransactionBuilderFactory: ConsensualTransactionBuilderFactory,
    @Reference(service = FlowEngine::class)
    private val flowEngine: FlowEngine,
    @Reference(service = ConsensualLedgerPersistenceService::class)
    private val persistenceService: ConsensualLedgerPersistenceService
) : ConsensualLedgerService, SingletonSerializeAsToken {

    @Suspendable
    override fun getTransactionBuilder(): ConsensualTransactionBuilder {
        return consensualTransactionBuilderFactory.create()
    }

    override fun fetchTransaction(id: SecureHash): ConsensualSignedTransaction? {
        return persistenceService.find(id)
    }

    @Suspendable
    override fun finality(
        signedTransaction: ConsensualSignedTransaction,
        sessions: List<FlowSession>
    ): ConsensualSignedTransaction {
        /*
        Need [doPrivileged] due to [contextLogger] being used in the flow's constructor.
        Creating the executing the SubFlow must be independent otherwise the security manager causes issues with Quasar.
        */
        val consensualFinalityFlow = try {
            AccessController.doPrivileged(PrivilegedExceptionAction {
                ConsensualFinalityFlow(signedTransaction, sessions)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
        return flowEngine.subFlow(consensualFinalityFlow)
    }

    @Suspendable
    override fun receiveFinality(
        session: FlowSession,
        verifier: ConsensualSignedTransactionVerifier
    ): ConsensualSignedTransaction {
        val consensualReceiveFinalityFlow = try {
            AccessController.doPrivileged(PrivilegedExceptionAction {
                ConsensualReceiveFinalityFlow(session, verifier)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
        return flowEngine.subFlow(consensualReceiveFinalityFlow)
    }
}
