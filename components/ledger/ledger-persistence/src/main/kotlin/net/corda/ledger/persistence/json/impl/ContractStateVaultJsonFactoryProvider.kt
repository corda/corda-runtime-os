package net.corda.ledger.persistence.json.impl

import net.corda.ledger.persistence.json.ContractStateVaultJsonFactoryStorage
import net.corda.sandbox.type.SandboxConstants
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandboxgroupcontext.CustomMetadataConsumer
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.getMetadataServices
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.query.json.ContractStateVaultJsonFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(
    service = [ UsedByPersistence::class ],
    property = [SandboxConstants.CORDA_MARKER_ONLY_SERVICE],
    scope = ServiceScope.PROTOTYPE
)
class ContractStateVaultJsonFactoryProvider @Activate constructor(
    @Reference(service = ContractStateVaultJsonFactoryStorage::class)
    private val factoryStorage: ContractStateVaultJsonFactoryStorage,

    // The default internal implementation of `ContractStateVaultJsonFactory`
    @Reference(service = ContractStateVaultJsonFactory::class)
    private val internalFactory: ContractStateVaultJsonFactory<ContractState>
) : UsedByPersistence, CustomMetadataConsumer {

    private companion object {
        private val logger = LoggerFactory.getLogger(ContractStateVaultJsonFactoryProvider::class.java)
    }

    init {
        logger.debug("Registering internal contract state json factory.")
        factoryStorage.registerJsonFactory(internalFactory)
    }

    @Suspendable
    override fun accept(context: MutableSandboxGroupContext) {
        val metadataServices = context.getMetadataServices<ContractStateVaultJsonFactory<*>>()

        if (logger.isDebugEnabled) {
            if (metadataServices.size == 1) {
                logger.debug("Found 1 contract state vault json factory.")
            } else {
                logger.debug("Found ${metadataServices.size} contract state vault json factory.")
            }
        }

        metadataServices.forEach {
            @Suppress("unchecked_cast")
            factoryStorage.registerJsonFactory(it as ContractStateVaultJsonFactory<ContractState>)
        }
    }
}
