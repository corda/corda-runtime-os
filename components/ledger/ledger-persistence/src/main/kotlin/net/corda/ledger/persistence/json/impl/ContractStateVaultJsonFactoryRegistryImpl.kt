package net.corda.ledger.persistence.json.impl

import net.corda.ledger.libs.persistence.json.ContractStateVaultJsonFactoryRegistry
import net.corda.sandbox.type.UsedByPersistence
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.query.json.ContractStateVaultJsonFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.TreeMap

@Suppress("unused")
@Component(
    service = [
        ContractStateVaultJsonFactoryRegistry::class,
        UsedByPersistence::class
    ],
    scope = ServiceScope.PROTOTYPE
)
class ContractStateVaultJsonFactoryRegistryImpl @Activate constructor() :
    ContractStateVaultJsonFactoryRegistry, UsedByPersistence {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(ContractStateVaultJsonFactoryRegistryImpl::class.java)
    }

    private val factoryStorage = TreeMap<String, ContractStateVaultJsonFactory<out ContractState>>()

    override fun registerJsonFactory(factory: ContractStateVaultJsonFactory<out ContractState>) {
        if (factoryStorage.putIfAbsent(factory.stateType.name, factory) != null) {
            logger.warn(
                "Failed to register ${ContractStateVaultJsonFactory::class.java.name} of ${factory::class.java.name} " +
                    "for state type ${factory.stateType::class.java.name} as ${factoryStorage[factory.stateType.name]} " +
                    "is already registered for the same type."
            )
            throw IllegalArgumentException("A factory for state class ${factory.stateType} is already registered.")
        }
    }

    override fun getFactoriesForClass(state: ContractState): List<ContractStateVaultJsonFactory<out ContractState>> {
        return factoryStorage.filter {
            it.value.stateType.isAssignableFrom(state::class.java)
        }.values.toList()
    }
}
