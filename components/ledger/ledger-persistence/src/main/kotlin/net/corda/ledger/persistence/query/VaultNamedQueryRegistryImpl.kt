package net.corda.ledger.persistence.query

import net.corda.sandbox.type.UsedByPersistence
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.query.VaultNamedQuery
import net.corda.v5.ledger.common.query.VaultNamedQueryRegistry
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

@Suppress("unused")
@Component(
    service = [
        VaultNamedQueryRegistry::class,
        UsedByPersistence::class
    ],
    scope = ServiceScope.PROTOTYPE
)
class VaultNamedQueryRegistryImpl @Activate constructor(
): VaultNamedQueryRegistry, UsedByPersistence {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    private val queryStorage = ConcurrentHashMap<String, VaultNamedQuery>()

    @Suspendable
    override fun getQuery(name: String): VaultNamedQuery? {
        return queryStorage[name]
    }

    @Suspendable
    override fun registerQuery(query: VaultNamedQuery) {
        require(queryStorage[query.name] == null) {
            // TODO should we just overwrite or ignore?
            "A query with name ${query.name} is already stored."
        }
        queryStorage[query.name] = query
    }
}