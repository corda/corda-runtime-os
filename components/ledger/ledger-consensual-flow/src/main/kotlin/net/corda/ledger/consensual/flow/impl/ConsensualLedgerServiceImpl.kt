@file:Suppress("deprecation")
package net.corda.ledger.consensual.flow.impl

import net.corda.ledger.consensual.flow.impl.flows.finality.ConsensualFinalityFlow
import net.corda.ledger.consensual.flow.impl.flows.finality.ConsensualReceiveFinalityFlow
import net.corda.ledger.consensual.flow.impl.persistence.ConsensualLedgerPersistenceService
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionInternal
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualTransactionBuilderImpl
import net.corda.ledger.consensual.flow.impl.transaction.factory.ConsensualSignedTransactionFactory
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionValidator
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.security.AccessController
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction

@Component(service = [ConsensualLedgerService::class, UsedByFlow::class], scope = PROTOTYPE)
class ConsensualLedgerServiceImpl @Activate constructor(
    @Reference(service = ConsensualSignedTransactionFactory::class)
    private val consensualSignedTransactionFactory: ConsensualSignedTransactionFactory,
    @Reference(service = FlowEngine::class)
    private val flowEngine: FlowEngine,
    @Reference(service = ConsensualLedgerPersistenceService::class)
    private val persistenceService: ConsensualLedgerPersistenceService
) : ConsensualLedgerService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun getTransactionBuilder(): ConsensualTransactionBuilder =
        ConsensualTransactionBuilderImpl(
            consensualSignedTransactionFactory
        )

    @Suspendable
    override fun findSignedTransaction(id: SecureHash): ConsensualSignedTransaction? {
        return persistenceService.find(id)
    }

    @Suspendable
    override fun findLedgerTransaction(id: SecureHash): ConsensualLedgerTransaction? {
        // For consensual ledger, it is ok to just resolve here - all it does is lazy deserialization
        return persistenceService.find(id)?.toLedgerTransaction()
    }

    @Suspendable
    override fun finalize(
        signedTransaction: ConsensualSignedTransaction,
        sessions: List<FlowSession>
    ): ConsensualSignedTransaction {
        /*
        Need [doPrivileged] due to [contextLogger] being used in the flow's constructor.
        Creating the executing the SubFlow must be independent otherwise the security manager causes issues with Quasar.
        */
        val consensualFinalityFlow = try {
            AccessController.doPrivileged(PrivilegedExceptionAction {
                ConsensualFinalityFlow(signedTransaction as ConsensualSignedTransactionInternal, sessions)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
        return flowEngine.subFlow(consensualFinalityFlow)
    }

    @Suspendable
    override fun receiveFinality(
        session: FlowSession,
        validator: ConsensualTransactionValidator
    ): ConsensualSignedTransaction {
        val consensualReceiveFinalityFlow = try {
            AccessController.doPrivileged(PrivilegedExceptionAction {
                ConsensualReceiveFinalityFlow(session, validator)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
        return flowEngine.subFlow(consensualReceiveFinalityFlow)
    }
}
