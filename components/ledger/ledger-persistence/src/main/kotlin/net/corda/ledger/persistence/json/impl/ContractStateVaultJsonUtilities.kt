package net.corda.ledger.persistence.json.impl

import com.fasterxml.jackson.core.JsonProcessingException
import net.corda.ledger.persistence.json.ContractStateVaultJsonFactoryStorage
import net.corda.ledger.persistence.utxo.UtxoTransactionReader
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.query.json.ContractStateVaultJsonFactory
import org.slf4j.LoggerFactory

object ContractStateVaultJsonUtilities {

    private val log = LoggerFactory.getLogger(ContractStateVaultJsonUtilities::class.java)

    fun extractJsonDataFromTransaction(
        transaction: UtxoTransactionReader,
        factoryStorage: ContractStateVaultJsonFactoryStorage,
        jsonMarshallingService: JsonMarshallingService
    ): String {
        val jsonMap = mutableMapOf<String, Any>()
        val factories = transaction.getProducedStates().firstOrNull()?.let {
            factoryStorage.getFactoriesForClass(it.state.contractState)
        } ?: emptyList()

        return try {
            // This block will fail with JsonProcessingException if either:
            // - the JSON string is invalid from any of the factories
            // - the concatenated map from the different factory outputs is invalid
            transaction.getProducedStates().forEach { stateAndRef ->
                factories.forEach { factory ->
                    jsonMap.putAll(
                        parseFactoryOutputToMap(stateAndRef, factory, jsonMarshallingService)
                    )
                }
            }

            jsonMarshallingService.format(jsonMap)
        } catch (e: JsonProcessingException) {
            log.warn("Error while processing JSON, reverting to empty JSON representation.")
            "{}" // Empty JSON string if we encountered an exception
        }
    }

    private fun parseFactoryOutputToMap(
        stateAndRef: StateAndRef<*>,
        factory: ContractStateVaultJsonFactory<ContractState>,
        jsonMarshallingService: JsonMarshallingService
    ): Map<String, Any> {
        // This block will fail with JsonProcessingException if the JSON string is invalid from the factory
        return jsonMarshallingService.parseMap(
            factory.create(stateAndRef.state.contractState, jsonMarshallingService),
            String::class.java,
            Any::class.java
        ).prefixKeysWithStateClass(factory.stateType)
    }

    private fun Map<String, Any>.prefixKeysWithStateClass(stateClass: Class<ContractState>) = mapKeys {
       "${stateClass.name}.${it.key}"
    }
}
