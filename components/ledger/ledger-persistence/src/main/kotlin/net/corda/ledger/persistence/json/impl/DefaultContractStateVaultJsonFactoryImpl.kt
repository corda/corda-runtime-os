package net.corda.ledger.persistence.json.impl

import net.corda.ledger.persistence.json.DefaultContractStateVaultJsonFactory
import net.corda.sandbox.type.UsedByPersistence
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.query.json.ContractStateVaultJsonFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope

class DefaultContractStateVaultJsonFactoryImpl : DefaultContractStateVaultJsonFactory {

    override fun create(stateAndRef: StateAndRef<*>, jsonMarshallingService: JsonMarshallingService): String {
        return jsonMarshallingService.format(ContractStateJson(stateAndRef.ref.toString()))
    }

    data class ContractStateJson(val stateRef: String)
}