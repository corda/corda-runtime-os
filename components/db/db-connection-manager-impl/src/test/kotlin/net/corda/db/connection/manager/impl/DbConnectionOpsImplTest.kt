package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.connection.manager.DbConnectionsRepository
import net.corda.db.core.CloseableDataSource
import net.corda.db.schema.CordaDb
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.util.UUID
import javax.persistence.EntityManagerFactory

class DbConnectionOpsImplTest {
    private val connectionId = UUID(4, 66)
    private val closeableDataSource = mock<CloseableDataSource>()
    private val closeableClusterDataSource = mock<CloseableDataSource>()
    private val dbConnectionsRepository = mock<DbConnectionsRepository> {
        on { create(connectionId) } doReturn closeableDataSource
        on { getClusterDataSource() } doReturn closeableClusterDataSource
    }
    private val clazz = DbConnectionOpsImplTest::class.java
    private val jpaEntitiesSet = mock<JpaEntitiesSet> {
        on { classes } doReturn setOf(clazz)
    }
    private val entitiesRegistry = mock<JpaEntitiesRegistry> {
        on { get(CordaDb.CordaCluster.persistenceUnitName) } doReturn jpaEntitiesSet
    }
    private val factory = mock<EntityManagerFactory>()
    private val entityManagerFactoryFactory = mock<EntityManagerFactoryFactory> {
        on {
            create(
                eq(connectionId.toString()),
                eq(listOf(clazz)),
                argThat {
                    this.dataSource == closeableDataSource
                },
            )
        } doReturn factory
    }
    private val impl = DbConnectionOpsImpl(
        dbConnectionsRepository,
        entitiesRegistry,
        entityManagerFactoryFactory,
    )

    @Test
    fun `getOrCreateEntityManagerFactory will call to createEntityManagerFactory`() {
        val createdFactory = impl.getOrCreateEntityManagerFactory(
            connectionId,
            jpaEntitiesSet,
        )

        assertThat(createdFactory).isSameAs(factory)
    }

    @Test
    fun `getOrCreateEntityManagerFactory will throw an exception for invalid ID`() {
        assertThrows<DBConfigurationException> {
            impl.getOrCreateEntityManagerFactory(
                UUID(50, 10),
                jpaEntitiesSet,
            )
        }
    }
}