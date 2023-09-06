package net.corda.messagebus.db.persistence

import net.corda.db.core.InMemoryDataSourceFactory
import net.corda.db.core.createDataSource
import net.corda.db.schema.DbSchema.DB_MESSAGE_BUS
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import javax.persistence.EntityManagerFactory

const val JDBC_URL = "jdbc.url"
const val USER = "user"
const val PASS = "pass"

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
        createDataSource(
            "org.postgresql.Driver",
            jdbcUrl,
            jdbcUser,
            jdbcPass
        )
    }

    return create(
        persistenceName,
        entities,
        DbEntityManagerConfiguration(dbSource),
    )
}
