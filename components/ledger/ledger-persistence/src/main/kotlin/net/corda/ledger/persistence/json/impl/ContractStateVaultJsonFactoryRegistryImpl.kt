package net.corda.ledger.persistence.json.impl

import net.corda.ledger.persistence.json.ContractStateVaultJsonFactoryRegistry
import net.corda.sandbox.type.UsedByPersistence
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.query.json.ContractStateVaultJsonFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

@Suppress("unused")
@Component(
    service = [
        ContractStateVaultJsonFactoryRegistry::class,
        UsedByPersistence::class
    ],
    scope = ServiceScope.PROTOTYPE
)
class ContractStateVaultJsonFactoryRegistryImpl @Activate constructor()
    : ContractStateVaultJsonFactoryRegistry, UsedByPersistence {

    private companion object {
        private val logger = LoggerFactory.getLogger(ContractStateVaultJsonFactoryRegistryImpl::class.java)
    }

    private val factoryStorage = ConcurrentHashMap<Class<out ContractState>, ContractStateVaultJsonFactory<out ContractState>>()

    override fun registerJsonFactory(factory: ContractStateVaultJsonFactory<out ContractState>) {
        if (factoryStorage.putIfAbsent(factory.stateType, factory) != null) {
            logger.warn("A factory for state class ${factory.stateType} is already registered.")
            throw IllegalArgumentException("A factory for state class ${factory.stateType} is already registered.")
        }
    }

    override fun getFactoriesForClass(state: ContractState): List<ContractStateVaultJsonFactory<out ContractState>> {
        return factoryStorage.filter {
            it.key.isAssignableFrom(state::class.java)
        }.values.toList()
    }
}
