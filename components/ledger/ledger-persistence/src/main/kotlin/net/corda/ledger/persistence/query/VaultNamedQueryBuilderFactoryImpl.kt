package net.corda.ledger.persistence.query

import net.corda.sandbox.type.UsedByPersistence
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.query.VaultNamedQueryBuilderFactory
import net.corda.v5.ledger.common.query.VaultNamedQueryCollector
import net.corda.v5.ledger.common.query.VaultNamedQueryFilter
import net.corda.v5.ledger.common.query.VaultNamedQueryRegistry
import net.corda.v5.ledger.common.query.VaultNamedQueryTransformer
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceScope
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
// TODO Should this class be thread-safe?
class VaultNamedQueryBuilderFactoryImpl @Activate constructor(
    @Reference(service = VaultNamedQueryRegistry::class, scope = ReferenceScope.PROTOTYPE)
    private val vaultNamedQueryRegistry: VaultNamedQueryRegistry
): VaultNamedQueryBuilderFactory, UsedByPersistence {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var name: String? = null
    private var whereJson: String? = null
    private var filter: VaultNamedQueryFilter<*>? = null
    private var mapper: VaultNamedQueryTransformer<*, *>? = null
    private var collector: VaultNamedQueryCollector<*, *>? = null

    @Suspendable
    override fun create(queryName: String): VaultNamedQueryBuilderFactory {
        if (logger.isDebugEnabled) {
            logger.debug("Creating custom query with name: $queryName")
        }
        this.name = queryName
        return this
    }
    @Suspendable
    override fun whereJson(json: String): VaultNamedQueryBuilderFactory {
        this.whereJson = json
        return this
    }
    @Suspendable
    override fun filter(filter: VaultNamedQueryFilter<*>): VaultNamedQueryBuilderFactory {
        this.filter = filter
        return this
    }
    @Suspendable
    override fun map(mapper: VaultNamedQueryTransformer<*, *>): VaultNamedQueryBuilderFactory {
        this.mapper = mapper
        return this
    }
    @Suspendable
    override fun collect(collector: VaultNamedQueryCollector<*, *>): VaultNamedQueryBuilderFactory {
        this.collector = collector
        return this
    }
    @Suspendable
    override fun register() {
        if (logger.isDebugEnabled) {
            logger.debug("Registering custom query with name: $name")
        }
        require(name != null) {
            "Named ledger query can't be registered without a name."
        }

        vaultNamedQueryRegistry.registerQuery(
            VaultNamedQueryImpl(
            name!!,
            whereJson,
            filter,
            mapper,
            collector
        )
        )
    }
}