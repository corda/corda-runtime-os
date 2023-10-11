package net.corda.ledger.persistence.query.impl

import net.corda.sandbox.type.SandboxConstants
import net.corda.sandbox.type.UsedByPersistence
import net.corda.v5.ledger.utxo.query.registration.VaultNamedQueryBuilderFactory
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [ DefaultVaultNamedQueryFactory::class, UsedByPersistence::class ],
    property = [SandboxConstants.CORDA_MARKER_ONLY_SERVICE],
    scope = ServiceScope.PROTOTYPE
)
class DefaultVaultNamedQueryFactoryImpl : DefaultVaultNamedQueryFactory, UsedByPersistence {

    private companion object {
        const val FIND_UNCONSUMED_STATES_BY_EXACT_TYPE = "FIND_UNCONSUMED_STATES_BY_EXACT_TYPE"
    }

    override fun create(vaultNamedQueryBuilderFactory: VaultNamedQueryBuilderFactory) {
        vaultNamedQueryBuilderFactory
            .create(FIND_UNCONSUMED_STATES_BY_EXACT_TYPE)
            .whereJson("WHERE visible_states.type = :type")
            .register()
    }
}