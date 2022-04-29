package net.corda.configuration.write.impl.tests.writer

import net.corda.configuration.write.impl.writer.ConfigEntityWriter
import net.corda.configuration.write.impl.writer.ConfigWriterProcessor
import net.corda.configuration.write.impl.writer.ConfigurationManagementResponseFuture
import net.corda.data.ExceptionEnvelope
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.libs.configuration.validation.ConfigurationValidationException
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.v5.base.versioning.Version
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.util.concurrent.*
import javax.persistence.RollbackException

/** Tests of [ConfigWriterProcessor]. */
class ConfigWriterProcessorTests {
    private val clock = mock<Clock>().apply {
        whenever(instant()).thenReturn(Instant.MIN)
    }
    private val validator = mock<ConfigurationValidator>().apply {
        whenever(validate(any(), any(), any(), any())).thenAnswer{ it.arguments[2] }
    }
    private val config = ConfigEntity("section", "{}", 1, 0, clock.instant(), "actor_one")
    private val configMgmtReq = config.run {
        ConfigurationManagementRequest(section, config, ConfigurationSchemaVersion(schemaVersionMajor, schemaVersionMinor),
            updateActor, version)
    }

    private val configEntityWriter = mock<ConfigEntityWriter>().apply {
        whenever(writeEntities(configMgmtReq, clock)).thenReturn(config)
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
        val processor = ConfigWriterProcessor(getPublisher(), configEntityWriter, validator, clock)
        processRequest(processor, configMgmtReq)

        verify(configEntityWriter).writeEntities(configMgmtReq, clock)
    }

    @Test
    fun `Config fails validation`() {
        doThrow(ConfigurationValidationException("", Version(0,0), SmartConfigImpl.empty(), emptySet()))
            .whenever(validator).validate(any(), any(), any(), any())
        val processor = ConfigWriterProcessor(getPublisher(), configEntityWriter, validator, clock)
        val response = processRequest(processor, configMgmtReq)

        assertThat(response.exception.errorType).isEqualTo("net.corda.libs.configuration.validation.ConfigurationValidationException")
    }

    @Test
    fun `publishes correct configuration to Kafka`() {
        val expectedRecord = Record(
            CONFIG_TOPIC,
            configMgmtReq.section,
            Configuration(configMgmtReq.config, configMgmtReq.version.toString())
        )

        val publisher = getPublisher()
        val processor = ConfigWriterProcessor(publisher, configEntityWriter, validator, clock)
        processRequest(processor, configMgmtReq)

        verify(publisher).publish(listOf(expectedRecord))
    }

    @Test
    fun `sends RPC success response after publishing configuration to Kafka`() {
        val expectedResp = configMgmtReq.run {
            ConfigurationManagementResponse(true, null, section, config, schemaVersion, version)
        }

        val processor = ConfigWriterProcessor(getPublisher(), configEntityWriter, validator, clock)
        val resp = processRequest(processor, configMgmtReq)

        assertEquals(expectedResp, resp)
    }

    @Test
    fun `writes configuration and audit data to the database even if publication to Kafka fails`() {
        val processor = ConfigWriterProcessor(getErroringPublisher(), configEntityWriter, validator, clock)
        processRequest(processor, configMgmtReq)

        verify(configEntityWriter).writeEntities(configMgmtReq, clock)
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

        val configEntityWriter = mock<ConfigEntityWriter>().apply {
            whenever(writeEntities(any(), any())).thenThrow(RollbackException())
        }
        val processor = ConfigWriterProcessor(getPublisher(), configEntityWriter, validator, clock)
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

        val processor = ConfigWriterProcessor(getErroringPublisher(), configEntityWriter, validator, clock)
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

        val configEntityWriter = mock<ConfigEntityWriter>().apply {
            whenever(writeEntities(any(), any())).thenThrow(RollbackException())
        }
        val processor = ConfigWriterProcessor(getPublisher(), configEntityWriter, validator, clock)
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

        val configEntityWriter = mock<ConfigEntityWriter>().apply {
            whenever(writeEntities(any(), any())).thenThrow(RollbackException())
        }
        val processor = ConfigWriterProcessor(getPublisher(), configEntityWriter, validator, clock)
        val resp = processRequest(processor, configMgmtReq)

        assertEquals(expectedResp, resp)
    }
}