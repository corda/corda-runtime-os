package net.corda.libs.configuration.write.impl.tests

import net.corda.data.ExceptionEnvelope
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.libs.configuration.datamodel.ConfigAuditEntity
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.libs.configuration.write.impl.ConfigWriterProcessor
import net.corda.libs.configuration.write.impl.ConfigurationManagementResponseFuture
import net.corda.libs.configuration.write.impl.dbutils.DBUtils
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import javax.persistence.RollbackException

/** Tests of [ConfigWriterProcessor]. */
class ConfigWriterProcessorTests {
    private val config = ConfigEntity("section", 1, "config_one", 1, "actor_one")
    private val configAudit = config.run { ConfigAuditEntity(section, config, version, updateActor) }
    private val configMgmtReq = config.run {
        ConfigurationManagementRequest(section, version, config, configVersion, updateActor)
    }

    /** Returns a mock [Publisher]. */
    private fun getPublisher() = mock<Publisher>().apply {
        whenever(publish(any())).thenReturn(listOf(CompletableFuture.completedFuture(Unit)))
    }

    /** Returns a mock [Publisher] that throws an error whenever it tries to publish. */
    private fun getErroringPublisher() = mock<Publisher>().apply {
        whenever(publish(any())).thenReturn(
            listOf(CompletableFuture.supplyAsync { throw CordaMessageAPIIntermittentException("") })
        )
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

    @Test
    fun `writes correct configuration and audit data to the database`() {
        val dbUtils = mock<DBUtils>()
        val processor = ConfigWriterProcessor(getPublisher(), dbUtils)
        processRequest(processor, configMgmtReq)

        verify(dbUtils).writeEntities(config, configAudit)
    }

    @Test
    fun `publishes correct configuration to Kafka`() {
        val expectedRecord = Record(
            CONFIG_TOPIC,
            configMgmtReq.section,
            Configuration(configMgmtReq.config, configMgmtReq.version.toString())
        )

        val publisher = getPublisher()
        val processor = ConfigWriterProcessor(publisher, mock())
        processRequest(processor, configMgmtReq)

        verify(publisher).publish(listOf(expectedRecord))
    }

    @Test
    fun `sends RPC success response after publishing configuration to Kafka`() {
        val expectedResp = ConfigurationManagementResponse(true, configMgmtReq.version, configMgmtReq.config)

        val processor = ConfigWriterProcessor(getPublisher(), mock())
        val resp = processRequest(processor, configMgmtReq)

        assertEquals(expectedResp, resp)
    }

    @Test
    fun `writes configuration and audit data to the database even if publication to Kafka fails`() {
        val dbUtils = mock<DBUtils>()
        val processor = ConfigWriterProcessor(getErroringPublisher(), dbUtils)
        processRequest(processor, configMgmtReq)

        verify(dbUtils).writeEntities(config, configAudit)
    }

    @Test
    fun `sends RPC failure response if fails to write configuration to the database`() {
        val expectedEnvelope = ExceptionEnvelope(
            RollbackException::class.java.name,
            "Entities $config and $configAudit couldn't be written to the database."
        )
        val expectedResp = ConfigurationManagementResponse(expectedEnvelope, config.version, config.config)

        val dbUtils = mock<DBUtils>().apply {
            whenever(writeEntities(any(), any())).thenThrow(RollbackException())
            whenever(readConfigEntity(config.section)).thenReturn(config)
        }
        val processor = ConfigWriterProcessor(getPublisher(), dbUtils)
        val resp = processRequest(processor, configMgmtReq)

        assertEquals(expectedResp, resp)
    }

    @Test
    fun `sends RPC failure response if fails to publish configuration to Kafka`() {
        val expectedRecord = configMgmtReq.run {
            Record(CONFIG_TOPIC, section, Configuration(config, version.toString()))
        }
        val expectedEnvelope = ExceptionEnvelope(
            ExecutionException::class.java.name,
            "Record $expectedRecord was written to the database, but couldn't be published."
        )
        val expectedResp = ConfigurationManagementResponse(expectedEnvelope, config.version, config.config)

        val dbUtils = mock<DBUtils>().apply {
            whenever(readConfigEntity(config.section)).thenReturn(config)
        }
        val processor = ConfigWriterProcessor(getErroringPublisher(), dbUtils)
        val resp = processRequest(processor, configMgmtReq)

        assertEquals(expectedResp, resp)
    }

    @Test
    fun `returns null configuration if there is no existing configuration for the given section when sending RPC failure response`() {
        val expectedEnvelope = ExceptionEnvelope(
            RollbackException::class.java.name,
            "Entities $config and $configAudit couldn't be written to the database."
        )
        val expectedResp = ConfigurationManagementResponse(expectedEnvelope, null, null)

        val dbUtils = mock<DBUtils>().apply {
            whenever(writeEntities(any(), any())).thenThrow(RollbackException())
        }
        val processor = ConfigWriterProcessor(getPublisher(), dbUtils)
        val resp = processRequest(processor, configMgmtReq)

        assertEquals(expectedResp, resp)
    }

    @Test
    fun `returns null configuration if configuration for the given section cannot be read back when sending RPC failure response`() {
        val expectedEnvelope = ExceptionEnvelope(
            RollbackException::class.java.name,
            "Entities $config and $configAudit couldn't be written to the database."
        )
        val expectedResp = ConfigurationManagementResponse(expectedEnvelope, null, null)

        val dbUtils = mock<DBUtils>().apply {
            whenever(writeEntities(any(), any())).thenThrow(RollbackException())
            whenever(readConfigEntity(any())).thenThrow(IllegalStateException())
        }
        val processor = ConfigWriterProcessor(getPublisher(), dbUtils)
        val resp = processRequest(processor, configMgmtReq)

        assertEquals(expectedResp, resp)
    }
}