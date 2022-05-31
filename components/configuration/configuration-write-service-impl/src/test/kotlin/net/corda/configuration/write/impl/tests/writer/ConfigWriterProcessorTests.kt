package net.corda.configuration.write.impl.tests.writer

import java.time.Clock
import java.time.Instant
import javax.persistence.RollbackException
import net.corda.configuration.write.impl.writer.ConfigEntityWriter
import net.corda.configuration.write.impl.writer.ConfigWriterProcessor
import net.corda.configuration.write.impl.writer.ConfigurationManagementResponseFuture
import net.corda.configuration.write.publish.ConfigPublishService
import net.corda.configuration.write.publish.ConfigurationDto
import net.corda.configuration.write.publish.ConfigurationSchemaVersionDto
import net.corda.data.ExceptionEnvelope
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.libs.configuration.validation.ConfigurationValidationException
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.v5.base.versioning.Version
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests of [ConfigWriterProcessor]. */
class ConfigWriterProcessorTests {
    companion object {
        fun ConfigurationManagementRequest.toConfigurationDto(): ConfigurationDto =
            ConfigurationDto(
                section,
                config,
                version,
                ConfigurationSchemaVersionDto(
                    schemaVersion.majorVersion,
                    schemaVersion.minorVersion
                )
            )
    }


    private val clock = mock<Clock>().apply {
        whenever(instant()).thenReturn(Instant.MIN)
    }
    private val validator = mock<ConfigurationValidator>().apply {
        whenever(validate(any(), any<Version>(), any(), any())).thenAnswer{ it.arguments[2] }
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
    private val configPublishService = mock<ConfigPublishService>()

    /** Returns a mock [Publisher] that throws an error whenever it tries to publish. */
    private fun getErroringPublishService() = mock<ConfigPublishService>().apply {
        whenever(put(any())).thenThrow(publisherError)
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
        val processor = ConfigWriterProcessor(configPublishService, configEntityWriter, validator, clock)
        processRequest(processor, configMgmtReq)

        verify(configEntityWriter).writeEntities(configMgmtReq, clock)
    }

    @Test
    fun `Config fails validation`() {
        doThrow(ConfigurationValidationException("", Version(0,0), SmartConfigImpl.empty(), emptySet()))
            .whenever(validator).validate(any(), any<Version>(), any(), any())
        val processor = ConfigWriterProcessor(configPublishService, configEntityWriter, validator, clock)
        val response = processRequest(processor, configMgmtReq)

        assertThat(response.exception.errorType).isEqualTo("net.corda.libs.configuration.validation.ConfigurationValidationException")
    }

    @Test
    fun `publishes correct configuration to Kafka`() {
        val expectedConfig = configMgmtReq.toConfigurationDto()

        val configPublishService = configPublishService
        val processor = ConfigWriterProcessor(configPublishService, configEntityWriter, validator, clock)
        processRequest(processor, configMgmtReq)

        verify(configPublishService).put(expectedConfig)
    }

    @Test
    fun `sends RPC success response after publishing configuration to Kafka`() {
        val expectedResp = configMgmtReq.run {
            ConfigurationManagementResponse(true, null, section, config, schemaVersion, version)
        }

        val processor = ConfigWriterProcessor(configPublishService, configEntityWriter, validator, clock)
        val resp = processRequest(processor, configMgmtReq)

        assertEquals(expectedResp, resp)
    }

    @Test
    fun `writes configuration and audit data to the database even if publication to Kafka fails`() {
        val processor = ConfigWriterProcessor(getErroringPublishService(), configEntityWriter, validator, clock)
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
        val processor = ConfigWriterProcessor(configPublishService, configEntityWriter, validator, clock)
        val resp = processRequest(processor, configMgmtReq)

        assertEquals(expectedResp, resp)
    }

    @Test
    fun `sends RPC failure response if fails to publish configuration to Kafka`() {
        val expectedConfig = configMgmtReq.toConfigurationDto()

        val expectedEnvelope = ExceptionEnvelope(
            CordaMessageAPIIntermittentException::class.java.name,
            "Configuration $expectedConfig was written to the database, but couldn't be published. Cause: " +
                    "$publisherError"
        )
        val expectedResp = configMgmtReq.run {
            ConfigurationManagementResponse(false, expectedEnvelope, section, config, schemaVersion, version)
        }

        val processor = ConfigWriterProcessor(getErroringPublishService(), configEntityWriter, validator, clock)
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
        val processor = ConfigWriterProcessor(configPublishService, configEntityWriter, validator, clock)
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
        val processor = ConfigWriterProcessor(configPublishService, configEntityWriter, validator, clock)
        val resp = processRequest(processor, configMgmtReq)

        assertEquals(expectedResp, resp)
    }
}