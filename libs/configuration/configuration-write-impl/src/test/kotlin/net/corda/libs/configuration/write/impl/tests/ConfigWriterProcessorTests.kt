package net.corda.libs.configuration.write.impl.tests

import net.corda.data.ExceptionEnvelope
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.libs.configuration.datamodel.ConfigAuditEntity
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.libs.configuration.write.impl.ConfigWriterProcessor
import net.corda.libs.configuration.write.impl.ConfigurationManagementResponseFuture
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ExecutionException
import javax.persistence.RollbackException

/** Tests of [ConfigWriterProcessor]. */
class ConfigWriterProcessorTests {
    private val config = ConfigEntity("section", 1, "config_one", 1, "actor_one")
    private val configAudit = config.run { ConfigAuditEntity(section, config, version, updateActor) }
    private val configMgmtReq = config.run {
        ConfigurationManagementRequest(section, version, config, configVersion, updateActor)
    }

    @Test
    fun `writes correct configuration and audit data to the database`() {
        val dbUtils = DummyDBUtils()
        val processor = ConfigWriterProcessor(DummyPublisher(), dbUtils)
        processRequest(processor, configMgmtReq)

        assertEquals(config, dbUtils.persistedConfig[config.section])
        assertTrue(dbUtils.persistedConfigAudit.contains(configAudit))
    }

    @Test
    fun `publishes correct configuration to Kafka`() {
        val publisher = DummyPublisher()
        val processor = ConfigWriterProcessor(publisher, DummyDBUtils())
        processRequest(processor, configMgmtReq)

        val recordConfig = Configuration(configMgmtReq.config, configMgmtReq.version.toString())
        val record = Record(CONFIG_TOPIC, configMgmtReq.section, recordConfig)
        assertEquals(listOf(record), publisher.publishedRecords)
    }

    @Test
    fun `sends RPC success response after publishing configuration to Kafka`() {
        val publisher = DummyPublisher()
        val processor = ConfigWriterProcessor(publisher, DummyDBUtils())
        val resp = processRequest(processor, configMgmtReq)

        val configMgmtResp = ConfigurationManagementResponse(true, configMgmtReq.version, configMgmtReq.config)
        assertEquals(configMgmtResp, resp)
    }

    @Test
    fun `writes configuration and audit data to the database even if publication to Kafka fails`() {
        val publisher = DummyPublisher(publishFails = true)
        val dbUtils = DummyDBUtils()
        val processor = ConfigWriterProcessor(publisher, dbUtils)
        processRequest(processor, configMgmtReq)

        assertEquals(config, dbUtils.persistedConfig[config.section])
        assertTrue(dbUtils.persistedConfigAudit.contains(configAudit))
    }

    @Test
    fun `sends RPC failure response if fails to write configuration to the database`() {
        val initialConfig = ConfigEntity(config.section, 0, "config_two", 1, "actor_two")
        val initialConfigMap = mapOf(initialConfig.section to initialConfig)

        val dbUtils = DummyDBUtils(initialConfigMap, writeFails = true)
        val processor = ConfigWriterProcessor(DummyPublisher(), dbUtils)
        val resp = processRequest(processor, configMgmtReq)

        val expectedEnvelope = ExceptionEnvelope(
            RollbackException::class.java.name,
            "Entities $config and $configAudit couldn't be written to the database."
        )
        val expectedResp = ConfigurationManagementResponse(
            expectedEnvelope, initialConfig.version, initialConfig.config
        )
        assertEquals(expectedResp, resp)
    }

    @Test
    fun `sends RPC failure response if fails to publish configuration to Kafka`() {
        val dbUtils = DummyDBUtils()
        val processor =
            ConfigWriterProcessor(DummyPublisher(publishFails = true), dbUtils)
        val resp = processRequest(processor, configMgmtReq)

        val expectedRecord = configMgmtReq.run {
            Record(CONFIG_TOPIC, section, Configuration(config, version.toString()))
        }
        val expectedEnvelope = ExceptionEnvelope(
            ExecutionException::class.java.name,
            "Record $expectedRecord was written to the database, but couldn't be published."
        )
        val expectedResp = ConfigurationManagementResponse(expectedEnvelope, config.version, config.config)
        assertEquals(expectedResp, resp)
    }

    @Test
    fun `returns null configuration if there is no existing configuration for the given section when sending RPC failure response`() {
        val newConfig = config.copy(section = "another_section")
        val newConfigAudit = configAudit.copy(section = newConfig.section)

        val req = newConfig.run {
            ConfigurationManagementRequest(section, version, config, configVersion, updateActor)
        }

        val dbUtils = DummyDBUtils(writeFails = true)
        val processor = ConfigWriterProcessor(DummyPublisher(), dbUtils)
        val resp = processRequest(processor, req)

        val expectedEnvelope = ExceptionEnvelope(
            RollbackException::class.java.name,
            "Entities $newConfig and $newConfigAudit couldn't be written to the database."
        )
        val expectedResp = ConfigurationManagementResponse(expectedEnvelope, null, null)
        assertEquals(expectedResp, resp)
    }

    @Test
    fun `returns null configuration if configuration for the given section cannot be read back when sending RPC failure response`() {
        val dbUtils = DummyDBUtils(writeFails = true, readFails = true)
        val processor = ConfigWriterProcessor(DummyPublisher(), dbUtils)
        val resp = processRequest(processor, configMgmtReq)

        val expectedEnvelope = ExceptionEnvelope(
            RollbackException::class.java.name,
            "Entities $config and $configAudit couldn't be written to the database."
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
}