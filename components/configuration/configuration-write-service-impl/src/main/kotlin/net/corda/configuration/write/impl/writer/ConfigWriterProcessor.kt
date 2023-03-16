package net.corda.configuration.write.impl.writer

import com.typesafe.config.ConfigFactory
import net.corda.configuration.write.publish.ConfigPublishService
import net.corda.data.ExceptionEnvelope
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.schema.Schemas.Config.CONFIG_TOPIC
import net.corda.v5.base.versioning.Version
import java.time.Clock

/**
 * An RPC responder processor that handles configuration management requests.
 *
 * Listens for configuration management requests over RPC. Persists the updated configuration to the cluster database
 * and publishes the updated configuration to Kafka.
 *
 * @property publisher The publisher used to publish to Kafka.
 * @property configEntityWriter The interface for interacting with configuration entities in the cluster database.
 * @property clock Controls how the current instant is determined, so it can be injected during testing.
 */
internal class ConfigWriterProcessor(
    private val configPublishService: ConfigPublishService,
    private val configEntityWriter: ConfigEntityWriter,
    private val validator: ConfigurationValidator,
    private val clock: Clock = Clock.systemUTC()
) : RPCResponderProcessor<ConfigurationManagementRequest, ConfigurationManagementResponse> {

    private val smartConfigFactory = SmartConfigFactory.createWithoutSecurityServices()

    /**
     * For each [request], the processor attempts to commit the updated config to the cluster database. If successful,
     * the updated config is then published by the [configPublishService] to the [CONFIG_TOPIC] topic for consumption using a
     * `ConfigReader`.
     *
     * If both steps succeed, [respFuture] is completed successfully. Otherwise, it is completed unsuccessfully.
     */
    override fun onNext(request: ConfigurationManagementRequest, respFuture: ConfigurationManagementResponseFuture) {
        // TODO - CORE-3318 - Ensure we don't perform any blocking operations in the processor.
        // TODO - CORE-3319 - Strategy for DB and Kafka retries.
        if (validateConfig(request, respFuture)) {
            val configEntity = publishConfigToDB(request, respFuture)
            if (configEntity != null) {
                publishConfigToKafka(configEntity, respFuture)
            }
        }
    }

    private fun validateConfig(
        req: ConfigurationManagementRequest,
        respFuture: ConfigurationManagementResponseFuture
    ): Boolean {
        return try {
            val configSection = req.section
            val configSchemaVersion = Version(req.schemaVersion.majorVersion, req.schemaVersion.minorVersion)
            val configValue = smartConfigFactory.create(ConfigFactory.parseString(req.config))
            validator.validate(
                configSection,
                configSchemaVersion,
                configValue,
                applyDefaults = false
            )
            true
        } catch (e: Exception) {
            val errMsg = "New configuration represented by $req couldn't be validated. Cause: $e"
            handleException(respFuture, errMsg, e, req.section, req.config, req.schemaVersion, req.version)
            false
        }
    }

    /**
     * Commits the updated config described by [req] to the database.
     *
     * If the transaction fails, the exception is caught and [respFuture] is completed unsuccessfully. The transaction
     * is not retried.
     *
     * @return The committed config entity if the commit was successful, or null otherwise.
     */
    private fun publishConfigToDB(
        req: ConfigurationManagementRequest,
        respFuture: ConfigurationManagementResponseFuture
    ): ConfigEntity? {
        return try {
            configEntityWriter.writeEntities(req, clock)
        } catch (e: Exception) {
            val errMsg = "New configuration represented by $req couldn't be written to the database. Cause: $e"
            handleException(respFuture, errMsg, e, req.section, req.config, req.schemaVersion, req.version)
            null
        }
    }

    /**
     * Publishes the updated config represented by [entity] on the [CONFIG_TOPIC] Kafka topic.
     *
     * If the publication fails, the exception is caught and [respFuture] is completed unsuccessfully. Publication is
     * not retried. Otherwise, [respFuture] is completed successfully.
     */
    private fun publishConfigToKafka(
        entity: ConfigEntity,
        respFuture: ConfigurationManagementResponseFuture
    ) {
        val configSection = entity.section
        try {
            configPublishService.put(
                configSection,
                entity.config,
                entity.version,
                ConfigurationSchemaVersion(entity.schemaVersionMajor, entity.schemaVersionMinor))
        } catch (e: Exception) {
            // TODO using the entity for now. Maybe we should be introducing a DTO for Configuration.
            val errMsg = "Configuration '$configSection' (${entity.config}) was written to the database, " +
                    "but couldn't be published. Cause: $e"
            handleException(
                respFuture, errMsg, e, configSection, entity.config,
                ConfigurationSchemaVersion(
                    entity.schemaVersionMajor,
                    entity.schemaVersionMinor
                ),
                entity.version
            )
            return
        }

        val response = ConfigurationManagementResponse(
            true,
            null,
            configSection,
            entity.config,
            ConfigurationSchemaVersion(
                entity.schemaVersionMajor,
                entity.schemaVersionMinor
            ),
            entity.version
        )
        respFuture.complete(response)
    }

    /** Completes the [respFuture] with an [ExceptionEnvelope]. */
    @Suppress("LongParameterList")
    private fun handleException(
        respFuture: ConfigurationManagementResponseFuture,
        errMsg: String,
        cause: Exception,
        section: String,
        config: String,
        schemaVersion: ConfigurationSchemaVersion,
        version: Int
    ) {
        val exception = ExceptionEnvelope(cause.javaClass.name, errMsg)
        val response = ConfigurationManagementResponse(false, exception, section, config, schemaVersion, version)
        respFuture.complete(response)
    }
}