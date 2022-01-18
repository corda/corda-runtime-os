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
        val config = Configuration(entity.config, entity.version.toString())
        val configRecord = Record(CONFIG_TOPIC, entity.section, config)
        // TODO - CORE-3404 - Check new config against current Kafka config to avoid overwriting.
        val future = publisher.publish(listOf(configRecord)).first()

        try {
            future.get()
        } catch (e: Exception) {
            val errMsg = "Record $configRecord was written to the database, but couldn't be published. Cause: $e"
            handleException(respFuture, errMsg, e, entity.section, entity.config, entity.schemaVersion, entity.version)
            return
        }

        val response = ConfigurationManagementResponse(
            true, null, entity.section, entity.config, entity.schemaVersion, entity.version
        )
        respFuture.complete(response)
    }

    /**
     * Completes the [respFuture] with an [ExceptionEnvelope].
     *
     * @throws IllegalStateException If the current configuration cannot be read back from the cluster database.
     */
    @Suppress("LongParameterList")
    private fun handleException(
        respFuture: ConfigurationManagementResponseFuture,
        errMsg: String,
        cause: Exception,
        section: String,
        config: String,
        schemaVersion: Int,
        version: Int
    ): Boolean {
        val exception = ExceptionEnvelope(cause.javaClass.name, errMsg)
        val response = ConfigurationManagementResponse(false, exception, section, config, schemaVersion, version)
        return respFuture.complete(response)
    }
}