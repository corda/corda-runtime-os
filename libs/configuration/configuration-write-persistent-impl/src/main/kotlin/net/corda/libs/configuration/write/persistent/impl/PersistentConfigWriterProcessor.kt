package net.corda.libs.configuration.write.persistent.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.write.persistent.TOPIC_CONFIG
import net.corda.libs.configuration.write.persistent.TOPIC_CONFIG_MGMT_REQUEST
import net.corda.libs.configuration.write.persistent.impl.entities.ConfigAuditEntity
import net.corda.libs.configuration.write.persistent.impl.entities.ConfigEntity
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import java.time.Instant
import java.util.concurrent.CompletableFuture
import javax.persistence.RollbackException

/**
 * An RPC responder processor that handles configuration management requests.
 *
 * Listens for configuration management requests on [TOPIC_CONFIG_MGMT_REQUEST]. Persists the updated
 * configuration to the cluster database and publishes the updated configuration to [TOPIC_CONFIG].
 */
internal class PersistentConfigWriterProcessor(
    private val publisher: Publisher,
    private val config: SmartConfig,
    private val dbUtils: DBUtils
) : RPCResponderProcessor<ConfigurationManagementRequest, ConfigurationManagementResponse> {

    private companion object {
        val logger = contextLogger()
    }

    /**
     * For each [request], the processor attempts to commit the updated config to the cluster database. If successful,
     * the updated config is then published by the [publisher] to the [TOPIC_CONFIG] topic for consumption using a
     * `ConfigReader`.
     *
     * If both steps succeed, [respFuture] is completed to indicate success. Otherwise, it is completed exceptionally.
     */
    override fun onNext(request: ConfigurationManagementRequest, respFuture: ConfigManagementResponseFuture) {
        logger.info("JJJ in onNext")
        if (publishConfigToDB(request, respFuture)) {
            publishConfigToKafka(request, respFuture)
        }
    }

    /**
     * Commits the updated config described by [req] to the database.
     *
     * If the transaction fails, the exception is caught and [respFuture] is completed exceptionally. The transaction
     * is not retried.
     *
     * @return True if the commit was successful, false if the commit failed.
     */
    private fun publishConfigToDB(
        req: ConfigurationManagementRequest,
        respFuture: ConfigManagementResponseFuture
    ): Boolean {
        // TODO - Joel - Get DB to generate timestamp.
        val now = Instant.now()
        val newConfig = ConfigEntity(req.section, req.version, req.configuration, now, req.updateActor)
        val newConfigAudit = ConfigAuditEntity(req.section, req.version, req.configuration, now, req.updateActor)

        return try {
            writeEntities(newConfig, newConfigAudit)
            true

        } catch (e: Exception) {
            val errMsg = "Entity $newConfig couldn't be written to the database."
            logger.debug("$errMsg Cause: $e.")

            val currentConfig = readConfigEntity(req.section)
            val exceptionEnvelope = ExceptionEnvelope(e.javaClass.name, errMsg)
            respFuture.complete(
                ConfigurationManagementResponse(exceptionEnvelope, currentConfig?.version, currentConfig?.configuration)
            )
            false
        }
    }

    /**
     * Publishes the updated config described by [req] on the [TOPIC_CONFIG] Kafka topic.
     *
     * If the publication fails, the exception is caught and [respFuture] is completed exceptionally. Publication is
     * not retried. Otherwise, we complete [respFuture] successfully.
     */
    private fun publishConfigToKafka(
        req: ConfigurationManagementRequest,
        respFuture: CompletableFuture<ConfigurationManagementResponse>
    ) {
        val configRecord = Record(TOPIC_CONFIG, req.section, Configuration(req.configuration, req.version.toString()))
        val future = publisher.publish(listOf(configRecord)).first()

        try {
            future.get()
            respFuture.complete(ConfigurationManagementResponse(true, req.version, req.configuration))

        } catch (e: Exception) {
            // TODO - Joel - Correct behaviour is to keep retrying publication, e.g. background reconciliation, but being careful not to overwrite new written config. Raise separate JIRA.
            val errMsg = "Record $configRecord was written to the database, but couldn't be published."
            logger.debug("$errMsg Cause: $e.")

            val exceptionEnvelope = ExceptionEnvelope(e.javaClass.name, errMsg)
            val response = ConfigurationManagementResponse(exceptionEnvelope, req.version, req.configuration)
            respFuture.complete(response)
        }
    }

    /**
     * Writes [newConfig] and [newConfigAudit] to the cluster database in a single transaction.
     *
     * @throws RollbackException If the database transaction cannot be committed.
     * @throws IllegalStateException/IllegalArgumentException/TransactionRequiredException If writing the entities
     *  fails for any other reason.
     */
    private fun writeEntities(newConfig: ConfigEntity, newConfigAudit: ConfigAuditEntity) {
        val entityManager = dbUtils.createEntityManager(config)

        try {
            entityManager.transaction.begin()
            entityManager.merge(newConfig)
            entityManager.persist(newConfigAudit)
            entityManager.transaction.commit()

        } finally {
            entityManager.close()
        }
    }

    /**
     * Reads the [ConfigEntity] for the specified [section]. Returns null if no config exists for the specified section.
     *
     * @throws IllegalStateException If the entity manager cannot be created.
     */
    private fun readConfigEntity(section: String): ConfigEntity? {
        val entityManager = dbUtils.createEntityManager(config)

        return try {
            entityManager.find(ConfigEntity::class.java, section)
        } finally {
            entityManager.close()
        }
    }
}