package net.corda.ledger.persistence.query.registration.impl

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
    @Reference(service = VaultNamedQueryBuilderFactory::class)
    private val vaultNamedQueryBuilderFactory: VaultNamedQueryBuilderFactory
) : UsedByPersistence, CustomMetadataConsumer {

    private companion object {
        const val FIND_UNCONSUMED_STATES_BY_EXACT_TYPE = "CORDA_FIND_UNCONSUMED_STATES_BY_EXACT_TYPE"
        val logger: Logger = LoggerFactory.getLogger(VaultNamedQueryFactoryProvider::class.java)
    }

    @Suspendable
    override fun accept(context: MutableSandboxGroupContext) {
        registerPlatformQueries(vaultNamedQueryBuilderFactory)

        val metadataServices = context.getMetadataServices<VaultNamedQueryFactory>()

        logger.debug { "Number of vault named queries found: ${metadataServices.size}" }

        metadataServices.forEach {
            it.create(vaultNamedQueryBuilderFactory)
        }
    }

    private fun registerPlatformQueries(vaultNamedQueryBuilderFactory: VaultNamedQueryBuilderFactory) {
        vaultNamedQueryBuilderFactory
            .create(FIND_UNCONSUMED_STATES_BY_EXACT_TYPE)
            .whereJson("WHERE visible_states.type = :type AND visible_states.consumed IS NULL")
            .register()
    }
}
