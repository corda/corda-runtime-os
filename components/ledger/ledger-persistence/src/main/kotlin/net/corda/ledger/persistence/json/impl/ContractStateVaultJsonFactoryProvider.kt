package net.corda.ledger.persistence.json.impl

import net.corda.ledger.libs.persistence.json.ContractStateVaultJsonFactoryRegistry
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(
    service = [ UsedByPersistence::class ],
    property = [SandboxConstants.CORDA_MARKER_ONLY_SERVICE],
    scope = ServiceScope.PROTOTYPE
)
class ContractStateVaultJsonFactoryProvider @Activate constructor(
    @Reference(service = ContractStateVaultJsonFactoryRegistry::class)
    private val factoryStorage: ContractStateVaultJsonFactoryRegistry,
) : UsedByPersistence, CustomMetadataConsumer {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(ContractStateVaultJsonFactoryProvider::class.java)
    }

    @Suspendable
    override fun accept(context: MutableSandboxGroupContext) {
        val metadataServices = context.getMetadataServices<ContractStateVaultJsonFactory<out ContractState>>()

        if (logger.isDebugEnabled) {
            logger.debug(
                "Number of contract state vault json factories found: ${metadataServices.size}, " +
                    "those are: $metadataServices"
            )
        }

        metadataServices.forEach {
            factoryStorage.registerJsonFactory(it)
        }
    }
}
