package net.corda.ledger.libs.persistence.json

import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.ledger.utxo.StateAndRef

interface DefaultContractStateVaultJsonFactory {

    fun create(stateAndRef: StateAndRef<*>, jsonMarshallingService: JsonMarshallingService): String
}
