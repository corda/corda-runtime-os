package net.corda.orm.impl

import net.corda.db.core.CloseableDataSource
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import org.hibernate.cfg.AvailableSettings
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl
import org.hibernate.jpa.boot.internal.PersistenceUnitInfoDescriptor
import org.hibernate.jpa.boot.spi.EntityManagerFactoryBuilder
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory
import javax.persistence.EntityManagerFactory
import javax.persistence.spi.PersistenceUnitInfo

/**
 * Hibernate implementation of [EntityManagerFactoryFactory]
 *
 * @constructor Create [EntityManagerFactoryFactory]
 */
@Component(service = [EntityManagerFactoryFactory::class])
class EntityManagerFactoryFactoryImpl(
    private val entityManagerFactoryBuilderFactory:
        (p: PersistenceUnitInfo) -> EntityManagerFactoryBuilder = { p ->
            EntityManagerFactoryBuilderImpl(PersistenceUnitInfoDescriptor(p), emptyMap<Any, Any>())
        }
) : EntityManagerFactoryFactory {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * [EntityManagerFactory] wrapper that closes [CloseableDataSource] when wrapped [EntityManagerFactory] is closed
     */
    private class EntityManagerFactoryWrapper(
        private val delegate: EntityManagerFactory,
        private val dataSource: CloseableDataSource
    ): EntityManagerFactory by delegate {

        override fun close() {
            delegate.close()
            dataSource.close()
        }
    }

    /**
     * Create [EntityManagerFactory]
     *
     * @param persistenceUnitName
     * @param entities to be managed by the [EntityManagerFactory]. No XML configuration needed.
     * @param configuration for the target data source
     * @return [EntityManagerFactory]
     */
    override fun create(
        persistenceUnitName: String,
        entities: List<Class<*>>,
        configuration: EntityManagerConfiguration
    ): EntityManagerFactory {
        return create(
            persistenceUnitName,
            entities.map { it.canonicalName },
            entities.map { it.classLoader }.distinct(),
            configuration
        )
    }


    override fun create(
        persistenceUnitName: String,
        entities: List<String>,
        classLoaders: List<ClassLoader>,
        configuration: EntityManagerConfiguration
    ): EntityManagerFactory {
        log.info("Creating for $persistenceUnitName")

        val props = mapOf(
            "hibernate.show_sql" to configuration.showSql.toString(),
            "hibernate.format_sql" to configuration.formatSql.toString(),
            "hibernate.connection.isolation" to configuration.transactionIsolationLevel.jdbcValue.toString(),
            "hibernate.hbm2ddl.auto" to configuration.ddlManage.convert(),
            "hibernate.jdbc.time_zone" to configuration.jdbcTimezone,
            // should these also be configurable?
            //
            // TODO - statistics integration isn't working in OSGi.
            // https://r3-cev.atlassian.net/browse/CORE-7168
            //"hibernate.generate_statistics" to true.toString(),
            "javax.persistence.validation.mode" to "none"
        ).toProperties()
        props[AvailableSettings.CLASSLOADERS] = classLoaders

        val persistenceUnitInfo = CustomPersistenceUnitInfo(
            persistenceUnitName,
            entities,
            props,
            configuration.dataSource
        )

        val entityManagerFactory = entityManagerFactoryBuilderFactory(persistenceUnitInfo).build()
        return EntityManagerFactoryWrapper(entityManagerFactory, configuration.dataSource)
    }
}

fun DdlManage.convert(): String {
    return when (this) {
        DdlManage.VALIDATE -> "validate"
        DdlManage.CREATE -> "create"
        DdlManage.UPDATE -> "update"
        else -> {
            "none"
        }
    }
}
