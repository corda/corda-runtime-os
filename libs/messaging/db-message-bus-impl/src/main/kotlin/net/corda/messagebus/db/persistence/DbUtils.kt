package net.corda.messagebus.db.persistence

import com.typesafe.config.Config
import net.corda.db.core.InMemoryDataSourceFactory
import net.corda.db.core.PostgresDataSourceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.db.schema.DbSchema.DB_MESSAGE_BUS
import net.corda.messagebus.api.configuration.getStringOrNull
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import javax.persistence.EntityManagerFactory

const val JDBC_URL = "jdbc.url"
const val USER = "user"
const val PASS = "pass"

fun EntityManagerFactoryFactory.create(
    dbConfig: SmartConfig,
    persistenceName: String,
    entities: List<Class<out Any>>,
): EntityManagerFactory {

    val jdbcUrl = dbConfig.getStringOrNull(JDBC_URL)

    val dbSource = if (jdbcUrl == null) {
        InMemoryDataSourceFactory().create(DB_MESSAGE_BUS)
    } else {
        val username = dbConfig.getString(USER)
        val pass = dbConfig.getString(PASS)
        PostgresDataSourceFactory().create(
            jdbcUrl,
            username,
            pass
        )
    }

    return create(
        persistenceName,
        entities,
        DbEntityManagerConfiguration(dbSource),
    )
}
