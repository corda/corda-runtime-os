package net.corda.messagebus.db.persistence

import net.corda.db.core.InMemoryDataSourceFactory
import net.corda.db.core.PostgresDataSourceFactory
import net.corda.db.schema.DbSchema.DB_MESSAGE_BUS
import net.corda.messagebus.db.configuration.ResolvedConsumerConfig
import net.corda.messagebus.db.configuration.ResolvedProducerConfig
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import javax.persistence.EntityManagerFactory

const val JDBC_URL = "jdbc.url"
const val USER = "user"
const val PASS = "pass"

fun EntityManagerFactoryFactory.create(
    dbConfig: ResolvedConsumerConfig,
    persistenceName: String,
    entities: List<Class<out Any>>,
): EntityManagerFactory {
    return create(dbConfig.jdbcUrl, dbConfig.jdbcUser, dbConfig.jdbcPass, persistenceName, entities)
}

fun EntityManagerFactoryFactory.create(
    dbConfig: ResolvedProducerConfig,
    persistenceName: String,
    entities: List<Class<out Any>>,
): EntityManagerFactory {
    return create(dbConfig.jdbcUrl, dbConfig.jdbcUser, dbConfig.jdbcPass, persistenceName, entities)
}


private fun EntityManagerFactoryFactory.create(jdbcUrl: String?,
                   jdbcUser: String,
                   jdbcPass: String,
                   persistenceName: String,
                   entities: List<Class<out Any>>
): EntityManagerFactory {
    val dbSource = if (jdbcUrl == null) {
        InMemoryDataSourceFactory().create(DB_MESSAGE_BUS)
    } else {
        PostgresDataSourceFactory().create(
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