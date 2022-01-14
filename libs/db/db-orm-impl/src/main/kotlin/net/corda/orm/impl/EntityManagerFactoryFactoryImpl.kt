package net.corda.orm.impl

import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.v5.base.util.contextLogger
import org.hibernate.cfg.AvailableSettings
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl
import org.hibernate.jpa.boot.internal.PersistenceUnitInfoDescriptor
import org.hibernate.jpa.boot.spi.EntityManagerFactoryBuilder
import org.osgi.service.component.annotations.Component
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
        private val log = contextLogger()
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
            "javax.persistence.validation.mode" to "none"
        ).toProperties()
        props[AvailableSettings.CLASSLOADERS] = classLoaders

        val persistenceUnitInfo = CustomPersistenceUnitInfo(
            persistenceUnitName,
            entities,
            props,
            configuration.dataSource
        )

        return entityManagerFactoryBuilderFactory(persistenceUnitInfo).build()
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
