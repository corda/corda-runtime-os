package net.corda.ledger.persistence.json.impl

import net.corda.crypto.cipher.suite.calculateHash
import net.corda.sandbox.type.UsedByPersistence
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.query.json.ContractStateVaultJsonFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope

@Suppress("unused")
@Component(
    service = [
        ContractStateVaultJsonFactory::class,
        UsedByPersistence::class
    ],
    scope = ServiceScope.PROTOTYPE
)
class ContractStateVaultJsonFactoryImpl @Activate constructor()
    : ContractStateVaultJsonFactory<ContractState>, UsedByPersistence {
    override fun getStateType(): Class<ContractState> = ContractState::class.java

    override fun create(state: ContractState, jsonMarshallingService: JsonMarshallingService): String {
        return """
            {
                "participants": "${state.participants.map { "\n${it.calculateHash()}\n" }}"
            }
        """.trimIndent()
    }
}