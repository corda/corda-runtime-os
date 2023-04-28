package com.r3.corda.demo.utxo.contract

import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.ledger.utxo.query.json.ContractStateVaultJsonFactory

class TestUtxoStateJsonFactory : ContractStateVaultJsonFactory<TestUtxoState> {
    override fun getStateType(): Class<TestUtxoState> = TestUtxoState::class.java

    override fun create(state: TestUtxoState, jsonMarshallingService: JsonMarshallingService): String {
        return """
            {
                "testField": "${state.testField}"
            }
        """.trimIndent()
    }
}