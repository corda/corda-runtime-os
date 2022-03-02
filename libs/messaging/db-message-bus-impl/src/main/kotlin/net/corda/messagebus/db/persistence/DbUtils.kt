package net.corda.messagebus.db.persistence

import com.typesafe.config.Config
import net.corda.db.core.InMemoryDataSourceFactory
import net.corda.db.core.PostgresDataSourceFactory
import net.corda.messagebus.api.configuration.getStringOrNull
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import javax.persistence.EntityManagerFactory


fun EntityManagerFactoryFactory.create(
    dbConfig: Config,
    persistenceName: String,
    entities: List<Class<out Any>>,
): EntityManagerFactory {

    val jdbcUrl = dbConfig.getStringOrNull("jdbc.url")
    val username = dbConfig.getStringOrNull("user")
    val pass = dbConfig.getStringOrNull("pass")

    val dbSource =
        if (jdbcUrl == null || username == null || pass == null || jdbcUrl.contains("hsqldb")) {
            InMemoryDataSourceFactory().create(persistenceName)
        } else {
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
