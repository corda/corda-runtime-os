package net.corda.libs.messaging.topic.factory

import com.typesafe.config.ConfigFactory
import net.corda.db.core.InMemoryDataSourceFactory
import net.corda.db.core.createDataSource
import net.corda.db.schema.DbSchema.DB_MESSAGE_BUS
import net.corda.libs.messaging.topic.DBTopicUtils
import net.corda.libs.messaging.topic.utils.TopicUtils
import net.corda.libs.messaging.topic.utils.factory.TopicUtilsFactory
import net.corda.messagebus.db.datamodel.CommittedPositionEntry
import net.corda.messagebus.db.datamodel.TopicEntry
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messagebus.db.persistence.JDBC_URL
import net.corda.messagebus.db.persistence.PASS
import net.corda.messagebus.db.persistence.USER
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.*


/**
 * DB implementation of [TopicUtilsFactory]
 * Used to create database instances of [TopicUtils]
 */
@Component(service = [TopicUtilsFactory::class])
class DBTopicUtilsFactory @Activate constructor(
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
) : TopicUtilsFactory {

    private companion object {
        const val CLIENT_ID = "client.id"
    }

    override fun createTopicUtils(props: Properties): TopicUtils {
        val config = ConfigFactory.parseProperties(props)
        val jdbcUrl = if (config.hasPath(JDBC_URL)) config.getString(JDBC_URL) else null
        val dbSource = if (jdbcUrl == null) {
            InMemoryDataSourceFactory().create(DB_MESSAGE_BUS)
        } else {
            val username = config.getString(USER)
            val pass = config.getString(PASS)
            createDataSource(
                "org.postgresql.Driver",
                jdbcUrl,
                username,
                pass
            )
        }

        return DBTopicUtils(
            entityManagerFactoryFactory.create(
                "DB Consumer for ${config.getString(CLIENT_ID)}",
                listOf(
                    TopicRecordEntry::class.java,
                    CommittedPositionEntry::class.java,
                    TopicEntry::class.java,
                    TransactionRecordEntry::class.java,
                ),
                DbEntityManagerConfiguration(dbSource)
            )
        )
    }
}
