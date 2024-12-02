package net.corda.ledger.persistence.json.impl

import net.corda.ledger.libs.persistence.json.DefaultContractStateVaultJsonFactory
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.ledger.utxo.StateAndRef

class DefaultContractStateVaultJsonFactoryImpl : DefaultContractStateVaultJsonFactory {

    override fun create(stateAndRef: StateAndRef<*>, jsonMarshallingService: JsonMarshallingService): String {
        return jsonMarshallingService.format(ContractStateJson(stateAndRef.ref.toString()))
    }

    data class ContractStateJson(val stateRef: String)
}
