package net.corda.messagebus.db.persistence

import net.corda.db.core.DataSourceFactory
import net.corda.db.core.DataSourceFactoryImpl
import net.corda.db.core.InMemoryDataSourceFactory
import net.corda.db.schema.DbSchema.DB_MESSAGE_BUS
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import java.time.Duration
import javax.persistence.EntityManagerFactory

const val JDBC_URL = "jdbc.url"
const val USER = "user"
const val PASS = "pass"

val dataSourceFactory: DataSourceFactory = DataSourceFactoryImpl()

internal fun EntityManagerFactoryFactory.create(
    jdbcUrl: String?,
    jdbcUser: String,
    jdbcPass: String,
    persistenceName: String,
    entities: List<Class<out Any>>
): EntityManagerFactory {
    val dbSource = if (jdbcUrl == null || jdbcUrl.contains("hsqldb", ignoreCase = true)) {
        InMemoryDataSourceFactory().create(DB_MESSAGE_BUS)
    } else {
        dataSourceFactory.create(
            enablePool = true,
            driverClass = "org.postgresql.Driver",
            jdbcUrl = jdbcUrl,
            username = jdbcUser,
            password = jdbcPass,
            maximumPoolSize = 5,
            minimumPoolSize = 1,
            idleTimeout = Duration.ofMinutes(2),
            maxLifetime = Duration.ofMinutes(30),
            keepaliveTime = Duration.ZERO,
            validationTimeout = Duration.ofSeconds(5),
        )
    }

    return create(
        persistenceName,
        entities,
        DbEntityManagerConfiguration(dbSource),
    )
}
