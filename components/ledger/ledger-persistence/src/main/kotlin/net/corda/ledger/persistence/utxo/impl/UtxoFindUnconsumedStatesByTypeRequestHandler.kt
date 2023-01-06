package net.corda.ledger.persistence.utxo.impl

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindUnconsumedStatesByType
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoOutputRecordFactory
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.messaging.api.records.Record
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.ContractState

@Suppress("LongParameterList")
class UtxoFindUnconsumedStatesByTypeRequestHandler(
    private val findUnconsumedStatesByType: FindUnconsumedStatesByType,
    private val sandbox: SandboxGroupContext,
    private val serializationService: SerializationService,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val utxoOutputRecordFactory: UtxoOutputRecordFactory
) : RequestHandler {

    @Suppress("UNCHECKED_CAST")
    override fun execute(): List<Record<*, *>> {
        val stateType = sandbox.sandboxGroup.loadClassFromMainBundles(findUnconsumedStatesByType.stateClassName)
        require(ContractState::class.java.isAssignableFrom(stateType)) {
            "Provided ${findUnconsumedStatesByType.stateClassName} is not type of ContractState"
        }

        // Find the relevant states of transaction
        val relevantStates = persistenceService.findUnconsumedRelevantStatesByType(
            stateType as Class<out ContractState>
        )

        // Return output records
        return listOf(
            utxoOutputRecordFactory.getFindUnconsumedStatesByTypeSuccessRecord(
                relevantStates,
                externalEventContext,
                serializationService
            )
        )
    }
}
