package net.corda.messagebus.db.persistence

import net.corda.messagebus.db.datamodel.CommittedPositionEntry
import net.corda.messagebus.db.datamodel.TopicEntry
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.utilities.trace
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import javax.persistence.EntityManagerFactory

/**
 * We don't want to use up all available database connections.  So we can
 * cache the individual [EntityManagerFactory] instances per jdbc connection for
 * re-use across all of the DB message bus.
 */
@Component(service = [EntityManagerFactoryHolder::class])
class EntityManagerFactoryHolder @Activate constructor(
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var emf: EntityManagerFactory? = null

    @Deactivate
    fun close() {
        emf?.close()
    }

    @Synchronized
    fun getEmf(
        jdbcUrl: String?,
        jdbcUsername: String,
        jdbcPassword: String,
    ): EntityManagerFactory {
        if (emf == null) {
            logger.trace { "Creating emf for $jdbcUrl" }
            emf = entityManagerFactoryFactory.create(
                jdbcUrl,
                jdbcUsername,
                jdbcPassword,
                "DB Message Bus for $jdbcUrl",
                listOf(
                    TopicRecordEntry::class.java,
                    CommittedPositionEntry::class.java,
                    TopicEntry::class.java,
                    TransactionRecordEntry::class.java,
                ),
            )
        }
        return emf ?: throw CordaRuntimeException("emf should never be null.")
    }
}
