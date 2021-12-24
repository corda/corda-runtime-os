package net.corda.libs.configuration.write.impl

import net.corda.config.schema.Schema.Companion.CONFIG_MGMT_REQUEST_TOPIC
import net.corda.data.ExceptionEnvelope
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.write.impl.dbutils.DBUtils
import net.corda.libs.configuration.write.impl.entities.ConfigAuditEntity
import net.corda.libs.configuration.write.impl.entities.ConfigEntity
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.v5.base.util.contextLogger

/**
 * An RPC responder processor that handles configuration management requests.
 *
 * Listens for configuration management requests on [CONFIG_MGMT_REQUEST_TOPIC]. Persists the updated
 * configuration to the cluster database and publishes the updated configuration to [CONFIG_TOPIC].
 */
internal class ConfigWriterProcessor(
    private val publisher: Publisher,
    private val config: SmartConfig,
    private val dbUtils: DBUtils
) : RPCResponderProcessor<ConfigurationManagementRequest, ConfigurationManagementResponse> {

    private companion object {
        val logger = contextLogger()
    }

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
        if (publishConfigToDB(request, respFuture)) {
            publishConfigToKafka(request, respFuture)
        }
    }

    /**
     * Commits the updated config described by [req] to the database.
     *
     * If the transaction fails, the exception is caught and [respFuture] is completed unsuccessfully. The transaction
     * is not retried.
     *
     * @return True if the commit was successful, false if the commit failed.
     */
    private fun publishConfigToDB(
        req: ConfigurationManagementRequest,
        respFuture: ConfigurationManagementResponseFuture
    ): Boolean {

        val newConfig = ConfigEntity(req.section, req.version, req.config, req.configVersion, req.updateActor)
        val newConfigAudit = ConfigAuditEntity(req.section, req.config, req.configVersion, req.updateActor)

        return try {
            dbUtils.writeEntities(config, newConfig, newConfigAudit)
            true
        } catch (e: Exception) {
            val errMsg = "Entity $newConfig couldn't be written to the database."
            handleException(respFuture, errMsg, e, req.section)
            false
        }
    }

    /**
     * Publishes the updated config described by [req] on the [CONFIG_TOPIC] Kafka topic.
     *
     * If the publication fails, the exception is caught and [respFuture] is completed unsuccessfully. Publication is
     * not retried. Otherwise, [respFuture] is completed successfully.
     */
    private fun publishConfigToKafka(
        req: ConfigurationManagementRequest,
        respFuture: ConfigurationManagementResponseFuture
    ) {

        val configRecord = Record(CONFIG_TOPIC, req.section, Configuration(req.config, req.version.toString()))
        val future = publisher.publish(listOf(configRecord)).first()

        try {
            future.get()
        } catch (e: Exception) {
            val errMsg = "Record $configRecord was written to the database, but couldn't be published."
            handleException(respFuture, errMsg, e, req.section)
            return
        }

        respFuture.complete(ConfigurationManagementResponse(true, req.version, req.config))
    }

    /**
     * Logs the [errMsg] and [cause], then completes the [respFuture] with an [ExceptionEnvelope].
     *
     * @throws IllegalStateException If the current configuration cannot be read back from the cluster database.
     */
    private fun handleException(
        respFuture: ConfigurationManagementResponseFuture, errMsg: String, cause: Exception, section: String
    ) {

        logger.debug("$errMsg Cause: $cause.")
        val currentConfig = try {
            dbUtils.readConfigEntity(config, section)
        } catch (e: IllegalStateException) {
            // We ignore this additional error, and report the one we were already planning to report.
            null
        }

        respFuture.complete(
            ConfigurationManagementResponse(
                ExceptionEnvelope(cause.javaClass.name, errMsg), currentConfig?.version, currentConfig?.config
            )
        )
    }
}