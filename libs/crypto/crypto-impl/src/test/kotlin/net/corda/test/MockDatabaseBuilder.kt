package net.corda.test

import net.corda.crypto.impl.SigningPersistentKey
import org.hibernate.SessionFactory
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.Configuration
import java.util.*

class MockDatabaseBuilder {
    companion object {
        fun resetDatabase(): SessionFactory {
            val cfg = Configuration()
                    .addAnnotatedClass(DefaultCryptoPersistentKey::class.java)
                    .addAnnotatedClass(SigningPersistentKey::class.java)
            StandardServiceRegistryBuilder().applySettings(dbSettings() ).build().let {
                return cfg.buildSessionFactory(it)
            }
        }

        private fun dbSettings() : Properties =  h2Settings()

        private fun h2Settings(databaseName: String = UUID.randomUUID().toString()) = Properties().apply {
            setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
            setProperty("hibernate.connection.driver_class", "org.h2.Driver")
            setProperty("hibernate.connection.url", "jdbc:h2:mem:$databaseName")
            setProperty("hibernate.connection.username", "sa")
            setProperty("hibernate.connection.password", "")
            setProperty("hibernate.hbm2ddl.auto", "update")
        }
    }
}
