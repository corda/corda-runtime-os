package net.corda.messagebus.db.persistence

import net.corda.messagebus.db.configuration.ResolvedConsumerConfig
import net.corda.messagebus.db.configuration.ResolvedProducerConfig
import net.corda.messagebus.db.datamodel.CommittedPositionEntry
import net.corda.messagebus.db.datamodel.TopicEntry
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.orm.EntityManagerFactoryFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ConcurrentHashMap
import javax.persistence.EntityManagerFactory

@Component(service = [EmfCache::class])
class EmfCache @Activate constructor(
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
) {
    companion object {
        const val INMEM_EMF = "InMemoryEmf"
    }

    private var emfs = ConcurrentHashMap<String, EntityManagerFactory>()

    @Deactivate
    fun close() {
        emfs.values.forEach { it.close() }
    }

    fun getEmf(resolvedConfig: ResolvedProducerConfig): EntityManagerFactory {
        val key = resolvedConfig.jdbcUrl ?: INMEM_EMF
        return emfs.computeIfAbsent(key) {
            entityManagerFactoryFactory.create(
                resolvedConfig,
                "DB Message Bus for $it",
                listOf(
                    TopicRecordEntry::class.java,
                    CommittedPositionEntry::class.java,
                    TopicEntry::class.java,
                    TransactionRecordEntry::class.java,
                ),
            )
        }
    }

    fun getEmf(resolvedConfig: ResolvedConsumerConfig): EntityManagerFactory {
        val key = resolvedConfig.jdbcUrl ?: INMEM_EMF
        return emfs.computeIfAbsent(key) {
            entityManagerFactoryFactory.create(
                resolvedConfig,
                "DB Message Bus for $it",
                listOf(
                    TopicRecordEntry::class.java,
                    CommittedPositionEntry::class.java,
                    TopicEntry::class.java,
                    TransactionRecordEntry::class.java,
                ),
            )
        }
    }

}
