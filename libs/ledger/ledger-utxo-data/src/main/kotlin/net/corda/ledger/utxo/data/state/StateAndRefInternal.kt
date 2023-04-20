package net.corda.ledger.utxo.data.state

import net.corda.ledger.utxo.data.transaction.UtxoTransactionOutputDto
import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef

interface StateAndRefInternal<T : ContractState> : StateAndRef<T> {
    fun toUtxoTransactionOutputDto(
        serializationService: SerializationService,
        currentSandboxGroup: SandboxGroup
    ): UtxoTransactionOutputDto
}