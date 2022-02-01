package net.corda.libs.configuration.write.impl.tests

import net.corda.data.ExceptionEnvelope
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.libs.configuration.write.impl.ConfigEntityRepository
import net.corda.libs.configuration.write.impl.ConfigWriterProcessor
import net.corda.libs.configuration.write.impl.ConfigurationManagementResponseFuture
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
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import javax.persistence.RollbackException

/** Tests of [ConfigWriterProcessor]. */
class ConfigWriterProcessorTests {
    private val clock = mock<Clock>().apply {
        whenever(instant()).thenReturn(Instant.MIN)
    }
    private val config = ConfigEntity("section", "config_one", 1, clock.instant(), "actor_one")
    private val configMgmtReq = config.run {
        ConfigurationManagementRequest(section, config, schemaVersion, updateActor, version)
    }

    private val configEntityRepository = mock<ConfigEntityRepository>().apply {
        whenever(writeEntities(configMgmtReq, clock)).thenReturn(config)
        whenever(readConfigEntity(config.section)).thenReturn(config)
    }
    private val publisherError = CordaMessageAPIIntermittentException("Error.")

    /** Returns a mock [Publisher]. */
    private fun getPublisher() = mock<Publisher>().apply {
        whenever(publish(any())).thenReturn(listOf(CompletableFuture.completedFuture(Unit)))
    }

    /** Returns a mock [Publisher] that throws an error whenever it tries to publish. */
    private fun getErroringPublisher() = mock<Publisher>().apply {
        whenever(publish(any())).thenReturn(
            listOf(CompletableFuture.supplyAsync { throw publisherError })
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
        val processor = ConfigWriterProcessor(getPublisher(), configEntityRepository, clock)
        processRequest(processor, configMgmtReq)

        verify(configEntityRepository).writeEntities(configMgmtReq, clock)
    }

    @Test
    fun `publishes correct configuration to Kafka`() {
        val expectedRecord = Record(
            CONFIG_TOPIC,
            configMgmtReq.section,
            Configuration(configMgmtReq.config, configMgmtReq.version.toString())
        )

        val publisher = getPublisher()
        val processor = ConfigWriterProcessor(publisher, configEntityRepository, clock)
        processRequest(processor, configMgmtReq)

        verify(publisher).publish(listOf(expectedRecord))
    }

    @Test
    fun `sends RPC success response after publishing configuration to Kafka`() {
        val expectedResp = configMgmtReq.run {
            ConfigurationManagementResponse(true, null, section, config, schemaVersion, version)
        }

        val processor = ConfigWriterProcessor(getPublisher(), configEntityRepository, clock)
        val resp = processRequest(processor, configMgmtReq)

        assertEquals(expectedResp, resp)
    }

    @Test
    fun `writes configuration and audit data to the database even if publication to Kafka fails`() {
        val processor = ConfigWriterProcessor(getErroringPublisher(), configEntityRepository, clock)
        processRequest(processor, configMgmtReq)

        verify(configEntityRepository).writeEntities(configMgmtReq, clock)
    }

    @Test
    fun `sends RPC failure response if fails to write configuration to the database`() {
        val expectedEnvelope = ExceptionEnvelope(
            RollbackException::class.java.name,
            "New configuration represented by $configMgmtReq couldn't be written to the database. Cause: " +
                    "${RollbackException()}"
        )
        val expectedResp = configMgmtReq.run {
            ConfigurationManagementResponse(false, expectedEnvelope, section, config, schemaVersion, version)
        }

        val configEntityRepository = mock<ConfigEntityRepository>().apply {
            whenever(writeEntities(any(), any())).thenThrow(RollbackException())
            whenever(readConfigEntity(config.section)).thenReturn(config)
        }
        val processor = ConfigWriterProcessor(getPublisher(), configEntityRepository, clock)
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
            "Record $expectedRecord was written to the database, but couldn't be published. Cause: " +
                    "${ExecutionException(publisherError)}"
        )
        val expectedResp = configMgmtReq.run {
            ConfigurationManagementResponse(false, expectedEnvelope, section, config, schemaVersion, version)
        }

        val processor = ConfigWriterProcessor(getErroringPublisher(), configEntityRepository, clock)
        val resp = processRequest(processor, configMgmtReq)

        assertEquals(expectedResp, resp)
    }

    @Test
    fun `sends RPC failure response if there is no existing configuration for the given section when sending RPC failure response`() {
        val expectedEnvelope = ExceptionEnvelope(
            RollbackException::class.java.name,
            "New configuration represented by $configMgmtReq couldn't be written to the database. Cause: " +
                    "${RollbackException()}"
        )
        val expectedResp = configMgmtReq.run {
            ConfigurationManagementResponse(false, expectedEnvelope, section, config, schemaVersion, version)
        }

        val configEntityRepository = mock<ConfigEntityRepository>().apply {
            whenever(writeEntities(any(), any())).thenThrow(RollbackException())
        }
        val processor = ConfigWriterProcessor(getPublisher(), configEntityRepository, clock)
        val resp = processRequest(processor, configMgmtReq)

        assertEquals(expectedResp, resp)
    }

    @Test
    fun `sends RPC failure response if configuration for the given section cannot be read back when sending RPC failure response`() {
        val expectedEnvelope = ExceptionEnvelope(
            RollbackException::class.java.name,
            "New configuration represented by $configMgmtReq couldn't be written to the database. Cause: " +
                    "${RollbackException()}"
        )
        val expectedResp = configMgmtReq.run {
            ConfigurationManagementResponse(false, expectedEnvelope, section, config, schemaVersion, version)
        }

        val configEntityRepository = mock<ConfigEntityRepository>().apply {
            whenever(writeEntities(any(), any())).thenThrow(RollbackException())
            whenever(readConfigEntity(any())).thenThrow(IllegalStateException())
        }
        val processor = ConfigWriterProcessor(getPublisher(), configEntityRepository, clock)
        val resp = processRequest(processor, configMgmtReq)

        assertEquals(expectedResp, resp)
    }
}