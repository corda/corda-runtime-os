package net.corda.messagebus.db.persistence

import com.typesafe.config.Config
import net.corda.db.core.PostgresDataSourceFactory
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import javax.persistence.EntityManagerFactory


fun EntityManagerFactoryFactory.create(
    dbConfig: Config,
    persistenceName: String,
    entities: List<Class<out Any>>,
): EntityManagerFactory {

    val jdbcUrl = dbConfig.getString("jdbc.url")
    val username = dbConfig.getString("user")
    val pass = dbConfig.getString("pass")

    val dbSource = PostgresDataSourceFactory().create(
        jdbcUrl,
        username,
        pass
    )

    return create(
        persistenceName,
        entities,
        DbEntityManagerConfiguration(dbSource),
    )
}
