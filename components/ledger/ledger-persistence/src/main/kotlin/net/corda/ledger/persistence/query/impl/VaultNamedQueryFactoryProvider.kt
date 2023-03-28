package net.corda.ledger.persistence.query.impl

import net.corda.sandbox.type.SandboxConstants
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandboxgroupcontext.CustomMetadataConsumer
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.getMetadataServices
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.query.VaultNamedQueryBuilderFactory
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceScope
import org.osgi.service.component.annotations.ServiceScope
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(
    service = [ UsedByPersistence::class ],
    property = [SandboxConstants.CORDA_MARKER_ONLY_SERVICE],
    scope = ServiceScope.PROTOTYPE
)
class VaultNamedQueryFactoryProvider @Activate constructor(
    @Reference(service = VaultNamedQueryBuilderFactory::class, scope = ReferenceScope.PROTOTYPE)
    private val vaultNamedQueryBuilderFactory: VaultNamedQueryBuilderFactory
) : UsedByPersistence, CustomMetadataConsumer {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suspendable
    override fun accept(context: MutableSandboxGroupContext) {
        val metadataServices = context.getMetadataServices<VaultNamedQueryFactory>()

        if (logger.isDebugEnabled) {
            if (metadataServices.size == 1) {
                logger.debug("Found 1 vault named query.")
            } else {
                logger.debug("Found ${metadataServices.size} vault named queries.")
            }
        }

        // TODO extra checks needed?
        metadataServices.forEach {
            it.create(vaultNamedQueryBuilderFactory)
        }
    }
}
