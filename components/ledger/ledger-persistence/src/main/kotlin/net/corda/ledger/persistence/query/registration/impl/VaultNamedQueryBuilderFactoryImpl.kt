package net.corda.ledger.persistence.query.registration.impl

import net.corda.ledger.persistence.query.parsing.VaultNamedQueryParser
import net.corda.ledger.persistence.query.registration.VaultNamedQueryRegistry
import net.corda.sandbox.type.UsedByPersistence
import net.corda.utilities.debug
import net.corda.v5.ledger.utxo.query.registration.VaultNamedQueryBuilder
import net.corda.v5.ledger.utxo.query.registration.VaultNamedQueryBuilderFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(
    service = [
        VaultNamedQueryBuilderFactory::class,
        UsedByPersistence::class
    ],
    scope = ServiceScope.PROTOTYPE
)
class VaultNamedQueryBuilderFactoryImpl @Activate constructor(
    @Reference
    private val vaultNamedQueryRegistry: VaultNamedQueryRegistry,
    @Reference
    private val vaultNamedQueryParser: VaultNamedQueryParser
) : VaultNamedQueryBuilderFactory, UsedByPersistence {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun create(queryName: String): VaultNamedQueryBuilder {
        logger.debug { "Creating vault named query with name: $queryName" }
        return VaultNamedQueryBuilderImpl(vaultNamedQueryRegistry, vaultNamedQueryParser, queryName)
    }
}
