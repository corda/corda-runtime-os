package net.corda.ledger.persistence.query.registration.impl

import net.corda.ledger.persistence.query.registration.VaultNamedQueryFactoryProvider
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
class VaultNamedQueryFactoryProviderImpl(
    private val delegate: VaultNamedQueryFactoryProvider
) : VaultNamedQueryFactoryProvider by delegate, UsedByPersistence, CustomMetadataConsumer {
    @Activate
    constructor(
        @Reference(service = VaultNamedQueryBuilderFactory::class)
        vaultNamedQueryBuilderFactory: VaultNamedQueryBuilderFactory
    ): this(MyVaultNamedQueryFactoryProviderImpl(vaultNamedQueryBuilderFactory))
}
