package net.corda.libs.configuration.write.impl.tests

import net.corda.data.config.ConfigurationManagementRequest
import net.corda.libs.configuration.datamodel.ConfigAuditEntity
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.libs.configuration.write.WrongVersionException
import net.corda.libs.configuration.write.impl.ConfigEntityRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

class ConfigEntityRepositoryTests {
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

    /** Creates a mock [EntityManagerFactory] that returns [entityManager] when asked to create an entity manager. */
    private fun getEntityManagerFactory(entityManager: EntityManager) = mock<EntityManagerFactory>().apply {
        whenever(createEntityManager()).thenReturn(entityManager)
    }

    @Test
    fun `persists correct config and config audit for a new section`() {
        val entityManager = getEntityManager()
        val configEntityRepository = ConfigEntityRepository(getEntityManagerFactory(entityManager))
        val mergedConfig = configEntityRepository.writeEntities(configMgmtReq, clock)

        assertEquals(config, mergedConfig)
        verify(entityManager).persist(configAudit)
        verify(entityManager).merge(config)
    }

    @Test
    fun `persists correct config and config audit for an existent section`() {
        val entityManager = getEntityManagerWithConfig()
        val configEntityRepository = ConfigEntityRepository(getEntityManagerFactory(entityManager))
        val mergedConfig = configEntityRepository.writeEntities(configMgmtReq, clock)

        assertEquals(config, mergedConfig)
        verify(entityManager).persist(configAudit)
        verify(entityManager).merge(config)
    }

    @Test
    fun `throws if asked to persist request with version that does not match existent section version`() {
        val configEntityRepository = ConfigEntityRepository(getEntityManagerFactory(getEntityManagerWithConfig()))
        val badVersionConfigMgmtReq = configMgmtReq.run {
            ConfigurationManagementRequest(section, config, schemaVersion, updateActor, version + 1)
        }
        val e = assertThrows<WrongVersionException> {
            configEntityRepository.writeEntities(badVersionConfigMgmtReq, clock)
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
        val configEntityRepository = ConfigEntityRepository(getEntityManagerFactory(entityManager))
        configEntityRepository.writeEntities(configMgmtReq, clock)

        verify(entityManager).close()
    }

    @Test
    fun `after failing to persist config and config audit, closes entity manager`() {
        val entityManager = getEntityManagerWithConfig()
        val configEntityRepository = ConfigEntityRepository(getEntityManagerFactory(entityManager))
        val badVersionConfigMgmtReq = configMgmtReq.run {
            ConfigurationManagementRequest(section, config, schemaVersion, updateActor, version + 1)
        }
        assertThrows<WrongVersionException> {
            configEntityRepository.writeEntities(badVersionConfigMgmtReq, clock)
        }

        verify(entityManager).close()
    }

    @Test
    fun `reads back matching config entity`() {
        val configEntityRepository = ConfigEntityRepository(getEntityManagerFactory(getEntityManagerWithConfig()))
        val foundConfigEntity = configEntityRepository.readConfigEntity(config.section)

        assertEquals(config, foundConfigEntity)
    }

    @Test
    fun `returns null if cannot read back matching config entity`() {
        val configEntityRepository = ConfigEntityRepository(getEntityManagerFactory(getEntityManager()))
        val foundConfigEntity = configEntityRepository.readConfigEntity("unknown_section")

        assertNull(foundConfigEntity)
    }

    @Test
    fun `after reading back config entity, closes entity manager`() {
        val entityManager = getEntityManagerWithConfig()
        val configEntityRepository = ConfigEntityRepository(getEntityManagerFactory(entityManager))
        configEntityRepository.readConfigEntity(config.section)

        verify(entityManager).close()
    }

    @Test
    fun `after failing to read back config entity, closes entity manager`() {
        val entityManager = getEntityManager()
        val configEntityRepository = ConfigEntityRepository(getEntityManagerFactory(entityManager))
        configEntityRepository.readConfigEntity("unknown_section")

        verify(entityManager).close()
    }
}