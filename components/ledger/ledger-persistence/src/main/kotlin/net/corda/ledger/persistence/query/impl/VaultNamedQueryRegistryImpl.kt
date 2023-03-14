package net.corda.ledger.persistence.query.impl

import net.corda.ledger.persistence.query.VaultNamedQueryRegistry
import net.corda.sandbox.type.UsedByPersistence
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
class VaultNamedQueryRegistryImpl @Activate constructor(): VaultNamedQueryRegistry, UsedByPersistence {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    private val queryStorage = ConcurrentHashMap<String, VaultNamedQuery>()

    override fun getQuery(name: String): VaultNamedQuery? {
        return queryStorage[name]
    }

    override fun registerQuery(query: VaultNamedQuery) {
        if (queryStorage.putIfAbsent(query.name, query) != null) {
            logger.warn("A query with name ${query.name} is already registered.")
            throw IllegalArgumentException("A query with name ${query.name} is already registered.")
        }
    }
}
