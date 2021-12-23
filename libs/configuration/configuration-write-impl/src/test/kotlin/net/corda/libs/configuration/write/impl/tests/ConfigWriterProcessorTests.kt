package net.corda.libs.configuration.write.impl.tests

import com.typesafe.config.ConfigFactory
import net.corda.data.ExceptionEnvelope
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactoryImpl
import net.corda.libs.configuration.write.impl.ConfigWriterProcessor
import net.corda.libs.configuration.write.impl.ConfigurationManagementResponseFuture
import net.corda.libs.configuration.write.impl.dbutils.DBUtils
import net.corda.libs.configuration.write.impl.entities.ConfigAuditEntity
import net.corda.libs.configuration.write.impl.entities.ConfigEntity
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Companion.CONFIG_TOPIC
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.supplyAsync
import java.util.concurrent.ExecutionException
import javax.persistence.RollbackException

/** Tests of [ConfigWriterProcessor]. */
class ConfigWriterProcessorTests {
    private val dummySmartConfig = SmartConfigFactoryImpl().create(ConfigFactory.empty())

    private val config = ConfigEntity("section", 1, "config_one", "actor_one")
    private val configAudit = config.run { ConfigAuditEntity(section, version, configuration, updateActor) }
    private val configMgmtReq = config.run {
        ConfigurationManagementRequest(section, version, configuration, updateActor)
    }

    @Test
    fun `writes correct configuration and audit data to the database`() {
        val dbUtils = DummyDBUtils()
        val processor = ConfigWriterProcessor(DummyPublisher(), dummySmartConfig, dbUtils)
        processRequest(processor, configMgmtReq)

        assertEquals(config, dbUtils.persistedConfig[config.section])
        assertTrue(dbUtils.persistedConfigAudit.contains(configAudit))
    }

    @Test
    fun `publishes correct configuration to Kafka`() {
        val publisher = DummyPublisher()
        val processor = ConfigWriterProcessor(publisher, dummySmartConfig, DummyDBUtils())
        processRequest(processor, configMgmtReq)

        val recordConfig = Configuration(configMgmtReq.configuration, configMgmtReq.version.toString())
        val record = Record(CONFIG_TOPIC, configMgmtReq.section, recordConfig)
        assertEquals(listOf(record), publisher.publishedRecords)
    }

    @Test
    fun `sends RPC success response after publishing configuration to Kafka`() {
        val publisher = DummyPublisher()
        val processor = ConfigWriterProcessor(publisher, dummySmartConfig, DummyDBUtils())
        val resp = processRequest(processor, configMgmtReq)

        val configMgmtResp = ConfigurationManagementResponse(true, configMgmtReq.version, configMgmtReq.configuration)
        assertEquals(configMgmtResp, resp)
    }

    @Test
    fun `writes configuration and audit data to the database even if publication to Kafka fails`() {
        val publisher = DummyPublisher(isPublishFails = true)
        val dbUtils = DummyDBUtils()
        val processor = ConfigWriterProcessor(publisher, dummySmartConfig, dbUtils)
        processRequest(processor, configMgmtReq)

        assertEquals(config, dbUtils.persistedConfig[config.section])
        assertTrue(dbUtils.persistedConfigAudit.contains(configAudit))
    }

    @Test
    fun `sends RPC failure response if fails to write configuration to the database`() {
        val initialConfig = ConfigEntity(config.section, 0, "config_two", "actor_two")
        val initialConfigMap = mapOf(initialConfig.section to initialConfig)

        val dbUtils = DummyDBUtils(initialConfigMap, isWriteFails = true)
        val processor = ConfigWriterProcessor(DummyPublisher(), dummySmartConfig, dbUtils)
        val resp = processRequest(processor, configMgmtReq)

        val expectedEnvelope = ExceptionEnvelope(
            RollbackException::class.java.name, "Entity $config couldn't be written to the database."
        )
        val expectedResp = ConfigurationManagementResponse(
            expectedEnvelope, initialConfig.version, initialConfig.configuration
        )
        assertEquals(expectedResp, resp)
    }

    @Test
    fun `sends RPC failure response if fails to publish configuration to Kafka`() {
        val dbUtils = DummyDBUtils()
        val processor =
            ConfigWriterProcessor(DummyPublisher(isPublishFails = true), dummySmartConfig, dbUtils)
        val resp = processRequest(processor, configMgmtReq)

        val expectedRecord = configMgmtReq.run {
            Record(CONFIG_TOPIC, section, Configuration(configuration, version.toString()))
        }
        val expectedEnvelope = ExceptionEnvelope(
            ExecutionException::class.java.name,
            "Record $expectedRecord was written to the database, but couldn't be published."
        )
        val expectedResp = ConfigurationManagementResponse(expectedEnvelope, config.version, config.configuration)
        assertEquals(expectedResp, resp)
    }

    @Test
    fun `returns null configuration if there is no existing configuration for the given section when sending RPC failure response`() {
        val newConfig = config.copy(section = "another_section")
        val req = ConfigurationManagementRequest(
            newConfig.section, newConfig.version, newConfig.configuration, newConfig.updateActor
        )

        val dbUtils = DummyDBUtils(isWriteFails = true)
        val processor = ConfigWriterProcessor(DummyPublisher(), dummySmartConfig, dbUtils)
        val resp = processRequest(processor, req)

        val expectedEnvelope = ExceptionEnvelope(
            RollbackException::class.java.name, "Entity $newConfig couldn't be written to the database."
        )
        val expectedResp = ConfigurationManagementResponse(expectedEnvelope, null, null)
        assertEquals(expectedResp, resp)
    }

    @Test
    fun `returns null configuration if configuration for the given section cannot be read back when sending RPC failure response`() {
        val dbUtils = DummyDBUtils(isWriteFails = true, isReadFails = true)
        val processor = ConfigWriterProcessor(DummyPublisher(), dummySmartConfig, dbUtils)
        val resp = processRequest(processor, configMgmtReq)

        val expectedEnvelope = ExceptionEnvelope(
            RollbackException::class.java.name, "Entity $config couldn't be written to the database."
        )
        val expectedResp = ConfigurationManagementResponse(expectedEnvelope, null, null)
        assertEquals(expectedResp, resp)
    }

    /** Calls [processor].`onNext` for the given [req], and returns the result of the future. */
    private fun processRequest(
        processor: ConfigWriterProcessor,
        req: ConfigurationManagementRequest
    ): ConfigurationManagementResponse {

        val respFuture = ConfigurationManagementResponseFuture()
        processor.onNext(req, respFuture)
        return respFuture.get()
    }

    /**
     * A [Publisher] that tracks [publishedRecords].
     *
     * Throws on publication if [isPublishFails].
     */
    private class DummyPublisher(private val isPublishFails: Boolean = false) : Publisher {
        var publishedRecords = mutableListOf<Record<*, *>>()

        override fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> = if (isPublishFails) {
            listOf(supplyAsync { throw CordaMessageAPIIntermittentException("") })
        } else {
            this.publishedRecords.addAll(records)
            listOf(CompletableFuture.completedFuture(Unit))
        }

        override fun publishToPartition(records: List<Pair<Int, Record<*, *>>>) = throw NotImplementedError()
        override fun close() = throw NotImplementedError()
    }

    /**
     * A [DBUtils] that tracks [persistedConfig] and [persistedConfigAudit].
     *
     * Throws on write if [isWriteFails]. Throws on read if [isReadFails].
     */
    private class DummyDBUtils(
        initialConfig: Map<String, ConfigEntity> = emptyMap(),
        private val isWriteFails: Boolean = false,
        private val isReadFails: Boolean = false
    ) : DBUtils {
        val persistedConfig = initialConfig.toMutableMap()
        val persistedConfigAudit
            get() = persistedConfig.values.map { config ->
                ConfigAuditEntity(config.section, config.version, config.configuration, config.updateActor)
            }

        override fun writeEntities(config: SmartConfig, newConfig: ConfigEntity, newConfigAudit: ConfigAuditEntity) {
            if (isWriteFails) {
                throw RollbackException()
            } else {
                this.persistedConfig[newConfig.section] = newConfig
            }
        }

        override fun readConfigEntity(config: SmartConfig, section: String) = if (isReadFails) {
            throw IllegalStateException()
        } else {
            this.persistedConfig[section]
        }

        override fun migrateClusterDatabase(config: SmartConfig) = throw NotImplementedError()
    }
}