package net.corda.configuration.write.impl.tests.writer

import net.corda.configuration.write.WrongConfigVersionException
import net.corda.configuration.write.impl.writer.ConfigEntityWriter
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.datamodel.ConfigAuditEntity
import net.corda.libs.configuration.datamodel.ConfigEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

class ConfigEntityWriterTests {
    private val clock = mock<Clock>().apply {
        whenever(instant()).thenReturn(Instant.MIN)
    }
    private val config = ConfigEntity("section", "a=b", 0, Instant.MIN, "actor")
    private val configAudit = ConfigAuditEntity(config)
    private val configMgmtReq = config.run {
        ConfigurationManagementRequest(section, config, schemaVersion, updateActor, version)
    }

    /** Creates a mock [EntityManager]. */
    private fun getEntityManager() = mock<EntityManager>().apply {
        whenever(transaction).thenReturn(mock())
        whenever(merge(config)).thenReturn(config)
    }

    /** Creates a mock [EntityManager] that returns [config] when queried for [config]'s section. */
    private fun getEntityManagerWithConfig() = getEntityManager().apply {
        whenever(find(ConfigEntity::class.java, config.section)).thenReturn(config)
    }

    /** Creates a mock [DbConnectionManager] that returns [entityManager] when asked to create an entity manager. */
    private fun getDbConnectionManager(entityManager: EntityManager): DbConnectionManager {
        val entityManagerFactory = mock<EntityManagerFactory>().apply {
            whenever(createEntityManager()).thenReturn(entityManager)
        }

        return mock<DbConnectionManager>().apply {
            whenever(clusterDbEntityManagerFactory).thenReturn(entityManagerFactory)
        }
    }

    @Test
    fun `persists correct config and config audit for a new section`() {
        val entityManager = getEntityManager()
        val configEntityWriter = ConfigEntityWriter(getDbConnectionManager(entityManager))
        val mergedConfig = configEntityWriter.writeEntities(configMgmtReq, clock)

        assertEquals(config, mergedConfig)
        verify(entityManager).persist(configAudit)
        verify(entityManager).merge(config)
    }

    @Test
    fun `persists correct config and config audit for an existing section`() {
        val entityManager = getEntityManagerWithConfig()
        val configEntityWriter = ConfigEntityWriter(getDbConnectionManager(entityManager))
        val mergedConfig = configEntityWriter.writeEntities(configMgmtReq, clock)

        assertEquals(config, mergedConfig)
        verify(entityManager).persist(configAudit)
        verify(entityManager).merge(config)
    }

    @Test
    fun `throws if asked to persist request with version that does not match existing section version`() {
        val configEntityWriter = ConfigEntityWriter(getDbConnectionManager(getEntityManagerWithConfig()))
        val badVersionConfigMgmtReq = configMgmtReq.run {
            ConfigurationManagementRequest(section, config, schemaVersion, updateActor, version + 1)
        }
        val e = assertThrows<WrongConfigVersionException> {
            configEntityWriter.writeEntities(badVersionConfigMgmtReq, clock)
        }

        assertEquals(
            "The request specified a version of ${badVersionConfigMgmtReq.version}, but the current version in the " +
                    "database is ${config.version}. These versions must match to update the cluster configuration.",
            e.message
        )
    }

    @Test
    fun `after persisting config and config audit, closes entity manager`() {
        val entityManager = getEntityManagerWithConfig()
        val configEntityWriter = ConfigEntityWriter(getDbConnectionManager(entityManager))
        configEntityWriter.writeEntities(configMgmtReq, clock)

        verify(entityManager).close()
    }

    @Test
    fun `after failing to persist config and config audit, closes entity manager`() {
        val entityManager = getEntityManagerWithConfig()
        val configEntityWriter = ConfigEntityWriter(getDbConnectionManager(entityManager))
        val badVersionConfigMgmtReq = configMgmtReq.run {
            ConfigurationManagementRequest(section, config, schemaVersion, updateActor, version + 1)
        }
        assertThrows<WrongConfigVersionException> {
            configEntityWriter.writeEntities(badVersionConfigMgmtReq, clock)
        }

        verify(entityManager).close()
    }
}