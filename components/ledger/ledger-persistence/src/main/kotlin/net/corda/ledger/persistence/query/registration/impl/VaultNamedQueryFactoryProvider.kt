package net.corda.ledger.persistence.query.registration.impl

import net.corda.ledger.persistence.query.impl.DefaultVaultNamedQueryFactory
import net.corda.sandbox.type.SandboxConstants
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandboxgroupcontext.CustomMetadataConsumer
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.getMetadataServices
import net.corda.utilities.debug
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFactory
import net.corda.v5.ledger.utxo.query.registration.VaultNamedQueryBuilderFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(
    service = [UsedByPersistence::class],
    property = [SandboxConstants.CORDA_MARKER_ONLY_SERVICE],
    scope = ServiceScope.PROTOTYPE
)
class VaultNamedQueryFactoryProvider @Activate constructor(
    @Reference(service = DefaultVaultNamedQueryFactory::class)
    private val defaultVaultNamedQueryFactory: DefaultVaultNamedQueryFactory,
    @Reference(service = VaultNamedQueryBuilderFactory::class)
    private val vaultNamedQueryBuilderFactory: VaultNamedQueryBuilderFactory
) : UsedByPersistence, CustomMetadataConsumer {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(VaultNamedQueryFactoryProvider::class.java)
    }

    @Suspendable
    override fun accept(context: MutableSandboxGroupContext) {
        val metadataServices = context.getMetadataServices<VaultNamedQueryFactory>() + defaultVaultNamedQueryFactory

        logger.debug { "Number of vault named queries found: ${metadataServices.size}" }

        metadataServices.forEach {
            it.create(vaultNamedQueryBuilderFactory)
        }
    }
}
