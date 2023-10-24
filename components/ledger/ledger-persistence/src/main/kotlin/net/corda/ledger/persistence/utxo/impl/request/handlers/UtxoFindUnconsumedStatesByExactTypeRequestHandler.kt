package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindUnconsumedStatesByExactType
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoOutputRecordFactory
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.messaging.api.records.Record
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.ledger.utxo.ContractState

@Suppress("LongParameterList")
class UtxoFindUnconsumedStatesByExactTypeRequestHandler(
    private val findUnconsumedStatesByExactType: FindUnconsumedStatesByExactType,
    private val sandbox: SandboxGroupContext,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val utxoOutputRecordFactory: UtxoOutputRecordFactory
) : RequestHandler {

    @Suppress("UNCHECKED_CAST")
    override fun execute(): List<Record<*, *>> {
        val stateType = sandbox.sandboxGroup.loadClassFromMainBundles(findUnconsumedStatesByExactType.stateClassName)
        require(ContractState::class.java.isAssignableFrom(stateType)) {
            "Provided ${findUnconsumedStatesByExactType.stateClassName} is not type of ContractState"
        }

        val visibleStates = persistenceService.findUnconsumedVisibleStatesByExactType(
            stateType as Class<out ContractState>
        )

        return listOf(utxoOutputRecordFactory.getStatesSuccessRecord(visibleStates, externalEventContext))
    }
}
