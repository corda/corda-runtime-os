package net.corda.libs.messaging.topic.factory

import com.typesafe.config.ConfigFactory
import net.corda.db.core.InMemoryDataSourceFactory
import net.corda.db.core.PostgresDataSourceFactory
import net.corda.db.schema.DbSchema.DB_MESSAGE_BUS
import net.corda.libs.messaging.topic.DBTopicUtils
import net.corda.libs.messaging.topic.utils.TopicUtils
import net.corda.libs.messaging.topic.utils.factory.TopicUtilsFactory
import net.corda.messagebus.api.configuration.ConfigProperties
import net.corda.messagebus.api.configuration.getStringOrNull
import net.corda.messagebus.db.datamodel.CommittedPositionEntry
import net.corda.messagebus.db.datamodel.TopicEntry
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
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

    override fun createTopicUtils(props: Properties): TopicUtils {
        val config = ConfigFactory.parseProperties(props)
        val jdbcUrl = config.getStringOrNull("jdbc.url")
        val dbSource = if (jdbcUrl == null) {
            InMemoryDataSourceFactory().create(DB_MESSAGE_BUS)
        } else {
            val username = config.getString("user")
            val pass = config.getString("pass")
            PostgresDataSourceFactory().create(
                jdbcUrl,
                username,
                pass
            )
        }

        return DBTopicUtils(
            entityManagerFactoryFactory.create(
                "DB Consumer for ${config.getString(ConfigProperties.CLIENT_ID)}",
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
