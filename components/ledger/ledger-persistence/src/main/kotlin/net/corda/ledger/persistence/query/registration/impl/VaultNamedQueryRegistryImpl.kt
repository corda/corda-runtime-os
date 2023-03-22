package net.corda.ledger.persistence.query.registration.impl

import net.corda.ledger.persistence.query.data.VaultNamedQuery
import net.corda.ledger.persistence.query.registration.VaultNamedQueryRegistry
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
        private val logger = LoggerFactory.getLogger(VaultNamedQueryRegistryImpl::class.java)
    }

    private val queryStorage = ConcurrentHashMap<String, VaultNamedQuery>()

    init {
        logger.info("VaultNamedQueryRegistryImpl created, storage: $queryStorage")
    }

    override fun getQuery(name: String): VaultNamedQuery? {
        logger.info("trying to fetch: $name, storage: $queryStorage")
        return queryStorage[name]
    }

    override fun registerQuery(query: VaultNamedQuery) {
        logger.info("Registering query: ${query.name}")
        if (queryStorage.putIfAbsent(query.name, query) != null) {
            logger.warn("A query with name ${query.name} is already registered.")
            throw IllegalArgumentException("A query with name ${query.name} is already registered.")
        }
        logger.info("storage query: $queryStorage")
    }
}
