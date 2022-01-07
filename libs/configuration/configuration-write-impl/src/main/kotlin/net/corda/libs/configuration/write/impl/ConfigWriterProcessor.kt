package net.corda.libs.configuration.write.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import java.time.Clock

/**
 * An RPC responder processor that handles configuration management requests.
 *
 * Listens for configuration management requests over RPC. Persists the updated configuration to the cluster database
 * and publishes the updated configuration to Kafka.
 *
 * @property publisher The publisher used to publish to Kafka.
 * @property configEntityRepository The interface for interacting with configuration entities in the cluster database.
 * @property clock Controls how the current instant is determined, so it can be injected during testing.
 */
internal class ConfigWriterProcessor(
    private val publisher: Publisher,
    private val configEntityRepository: ConfigEntityRepository,
    private val clock: Clock = Clock.systemUTC()
) : RPCResponderProcessor<ConfigurationManagementRequest, ConfigurationManagementResponse> {

    /**
     * For each [request], the processor attempts to commit the updated config to the cluster database. If successful,
     * the updated config is then published by the [publisher] to the [CONFIG_TOPIC] topic for consumption using a
     * `ConfigReader`.
     *
     * If both steps succeed, [respFuture] is completed successfully. Otherwise, it is completed unsuccessfully.
     */
    override fun onNext(request: ConfigurationManagementRequest, respFuture: ConfigurationManagementResponseFuture) {
        // TODO - CORE-3318 - Ensure we don't perform any blocking operations in the processor.
        // TODO - CORE-3319 - Strategy for DB and Kafka retries.
        val configEntity = publishConfigToDB(request, respFuture)
        if (configEntity != null) {
            publishConfigToKafka(configEntity, respFuture)
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
            configEntityRepository.writeEntities(req, clock)
        } catch (e: Exception) {
            // TODO - Joel - Return special exception envelope for incompatible version number.
            val errMsg = "New configuration represented by $req couldn't be written to the database. Cause: $e"
            handleException(respFuture, errMsg, e, req.section)
            null
        }
    }

    /**
     * Publishes the updated config represented by [configEntity] on the [CONFIG_TOPIC] Kafka topic.
     *
     * If the publication fails, the exception is caught and [respFuture] is completed unsuccessfully. Publication is
     * not retried. Otherwise, [respFuture] is completed successfully.
     */
    private fun publishConfigToKafka(
        configEntity: ConfigEntity,
        respFuture: ConfigurationManagementResponseFuture
    ) {
        val config = Configuration(configEntity.config, configEntity.version.toString())
        val configRecord = Record(CONFIG_TOPIC, configEntity.section, config)
        val future = publisher.publish(listOf(configRecord)).first()

        try {
            future.get()
        } catch (e: Exception) {
            val errMsg = "Record $configRecord was written to the database, but couldn't be published. Cause: $e"
            handleException(respFuture, errMsg, e, configEntity.section)
            return
        }

        respFuture.complete(ConfigurationManagementResponse(true, configEntity.version, configEntity.config))
    }

    /**
     * Logs the [errMsg] and [cause], then completes the [respFuture] with an [ExceptionEnvelope].
     *
     * @throws IllegalStateException If the current configuration cannot be read back from the cluster database.
     */
    private fun handleException(
        respFuture: ConfigurationManagementResponseFuture, errMsg: String, cause: Exception, section: String
    ) {
        val currentConfig = try {
            configEntityRepository.readConfigEntity(section)
        } catch (e: IllegalStateException) {
            // We ignore this additional error, and report the one we were already planning to report.
            null
        }

        respFuture.complete(
            ConfigurationManagementResponse(
                // TODO - Joel - Consider returning "{}" and -1 instead of nulls.
                ExceptionEnvelope(cause.javaClass.name, errMsg), currentConfig?.version, currentConfig?.config
            )
        )
    }
}