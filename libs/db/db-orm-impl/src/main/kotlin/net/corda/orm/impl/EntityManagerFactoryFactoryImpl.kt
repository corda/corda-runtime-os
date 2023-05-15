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
import java.time.Instant
import java.util.UUID
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
    private val clock = java.time.Clock.systemUTC()

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
        val start = clock.instant()
        var curr: Instant
        var last = start
        val id = UUID.randomUUID()
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
            "hibernate.generate_statistics" to true.toString(),
            "hibernate.show_sql" to true.toString(),
            "hibernate.format_sql" to true.toString(),
            "javax.persistence.validation.mode" to "none"
        ).toProperties()
        props[AvailableSettings.CLASSLOADERS] = classLoaders

        curr = clock.instant()
        log.info(
            "DB investigation " +
                    "- override fun create(persistenceUnitName: String, entities: List<String>, classLoaders: " +
                    "List<ClassLoader>, configuration: EntityManagerConfiguration): EntityManagerFactory " +
                    "- 1 " +
                    "- $id " +
                    "- Current: ${curr.nano} " +
                    "- Since last checkpoint: ${curr.nano - last.nano}ns" +
                    "- Since last checkpoint: ${curr.toEpochMilli() - last.toEpochMilli()}ms" +
                    "- Since last checkpoint: ${curr.epochSecond - last.epochSecond}s"
        )
        last = curr

        val persistenceUnitInfo = CustomPersistenceUnitInfo(
            persistenceUnitName,
            entities,
            props,
            configuration.dataSource
        )
        curr = clock.instant()
        log.info(
            "DB investigation " +
                    "- override fun create(persistenceUnitName: String, entities: List<String>, classLoaders: " +
                    "List<ClassLoader>, configuration: EntityManagerConfiguration): EntityManagerFactory " +
                    "- 2 " +
                    "- $id " +
                    "- Current: ${curr.nano} " +
                    "- Since last checkpoint: ${curr.nano - last.nano}ns" +
                    "- Since last checkpoint: ${curr.toEpochMilli() - last.toEpochMilli()}ms" +
                    "- Since last checkpoint: ${curr.epochSecond - last.epochSecond}s"
        )
        last = curr

        val entityManagerFactory = entityManagerFactoryBuilderFactory(persistenceUnitInfo).also {
            curr = clock.instant()
            log.info(
                "DB investigation " +
                        "- override fun create(persistenceUnitName: String, entities: List<String>, classLoaders: " +
                        "List<ClassLoader>, configuration: EntityManagerConfiguration): EntityManagerFactory " +
                        "- 3 " +
                        "- $id " +
                        "- Current: ${curr.nano} " +
                        "- Since last checkpoint: ${curr.nano - last.nano}ns" +
                        "- Since last checkpoint: ${curr.toEpochMilli() - last.toEpochMilli()}ms" +
                        "- Since last checkpoint: ${curr.epochSecond - last.epochSecond}s"
            )
            last = curr
        }.build().also {
            curr = clock.instant()
            log.info(
                "DB investigation " +
                        "- override fun create(persistenceUnitName: String, entities: List<String>, classLoaders: " +
                        "List<ClassLoader>, configuration: EntityManagerConfiguration): EntityManagerFactory " +
                        "- 4 " +
                        "- $id " +
                        "- Current: ${curr.nano} " +
                        "- Since last checkpoint: ${curr.nano - last.nano}ns" +
                        "- Since last checkpoint: ${curr.toEpochMilli() - last.toEpochMilli()}ms" +
                        "- Since last checkpoint: ${curr.epochSecond - last.epochSecond}s"
            )
            last = curr
        }
        return EntityManagerFactoryWrapper(entityManagerFactory, configuration.dataSource).also {
            curr = clock.instant()
            log.info(
                "DB investigation " +
                        "- override fun create(persistenceUnitName: String, entities: List<String>, classLoaders: " +
                        "List<ClassLoader>, configuration: EntityManagerConfiguration): EntityManagerFactory " +
                        "- 5 " +
                        "- $id " +
                        "- Current: ${curr.nano} " +
                        "- Since last checkpoint: ${curr.nano - last.nano}ns" +
                        "- Since last checkpoint: ${curr.toEpochMilli() - last.toEpochMilli()}ms" +
                        "- Since last checkpoint: ${curr.epochSecond - last.epochSecond}s"
            )
            log.info(
                "DB investigation " +
                        "- override fun create(persistenceUnitName: String, entities: List<String>, classLoaders: " +
                        "List<ClassLoader>, configuration: EntityManagerConfiguration): EntityManagerFactory " +
                        "- total " +
                        "- $id " +
                        "- Since start: ${curr.nano - start.nano}ns" +
                        "- Since start: ${curr.toEpochMilli() - start.toEpochMilli()}ms" +
                        "- Since start: ${curr.epochSecond - start.epochSecond}s"
            )
        }
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
