package net.corda.persistence.common

import net.corda.sandboxgroupcontext.RequireSandboxAMQP
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.ContractState
import javax.persistence.EntityManagerFactory
import net.corda.v5.ledger.utxo.observer.UtxoTokenTransactionStateObserver

/**
 *  Keys to look up the per-entity sandbox objects.
 */
object EntitySandboxContextTypes {
    const val SANDBOX_EMF = "ENTITY_MANAGER_FACTORY"
    const val SANDBOX_TOKEN_STATE_OBSERVERS = "SANDBOX_TOKEN_STATE_OBSERVERS"
    const val SANDBOX_TOKEN_STATE_OBSERVERS_V2 = "SANDBOX_TOKEN_STATE_OBSERVERS_V2"
}

fun SandboxGroupContext.getSerializationService(): SerializationService =
    getObjectByKey(RequireSandboxAMQP.AMQP_SERIALIZATION_SERVICE)
        ?: throw CordaRuntimeException(
            "Entity serialization service not found within the sandbox for identity: " +
                    "${virtualNodeContext.holdingIdentity}"
        )

fun SandboxGroupContext.getEntityManagerFactory(): EntityManagerFactory =
    getObjectByKey(EntitySandboxContextTypes.SANDBOX_EMF)
        ?: throw CordaRuntimeException(
            "Entity manager factory not found within the sandbox for identity: " +
                    "${virtualNodeContext.holdingIdentity}"
        )

@Suppress("DEPRECATION")
fun SandboxGroupContext.getTokenStateObservers()
        : Map<Class<out ContractState>, net.corda.v5.ledger.utxo.observer.UtxoLedgerTokenStateObserver<ContractState>?> =
    getTokenStateObservers(EntitySandboxContextTypes.SANDBOX_TOKEN_STATE_OBSERVERS)

fun SandboxGroupContext.getTokenStateObserversV2()
        : Map<Class<out ContractState>, UtxoTokenTransactionStateObserver<ContractState>?> =
    getTokenStateObservers(EntitySandboxContextTypes.SANDBOX_TOKEN_STATE_OBSERVERS_V2)


private fun <T> SandboxGroupContext.getTokenStateObservers(sandboxContextTypes: String)
        : Map<Class<out ContractState>, T> = getObjectByKey(sandboxContextTypes) ?: throw CordaRuntimeException(
    "Token State Observers not found within the sandbox for identity: ${virtualNodeContext.holdingIdentity}"
)
