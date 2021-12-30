package net.corda.libs.configuration.write.impl.tests

import net.corda.libs.configuration.datamodel.ConfigAuditEntity
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.libs.configuration.write.impl.ConfigurationManagementRPCSubscription
import net.corda.libs.configuration.write.impl.dbutils.DBUtils
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import java.util.concurrent.CompletableFuture
import javax.persistence.RollbackException

/** A [ConfigurationManagementRPCSubscription] that tracks whether the subscription has been started. */
internal class DummyRPCSubscription : ConfigurationManagementRPCSubscription {
    override var isRunning = false
    override val subscriptionName get() = throw NotImplementedError()

    override fun start() {
        isRunning = true
    }

    override fun stop() {
        isRunning = false
    }
}

/**
 * A [Publisher] that tracks whether it has been started, and which [publishedRecords] it has published.
 *
 * Throws on publication if [publishFails].
 */
internal class DummyPublisher(private val publishFails: Boolean = false) : Publisher {
    var isStarted = false
    var publishedRecords = mutableListOf<Record<*, *>>()

    override fun start() {
        isStarted = true
    }

    override fun close() {
        isStarted = false
    }

    override fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> = if (publishFails) {
        listOf(CompletableFuture.supplyAsync { throw CordaMessageAPIIntermittentException("") })
    } else {
        this.publishedRecords.addAll(records)
        listOf(CompletableFuture.completedFuture(Unit))
    }

    override fun publishToPartition(records: List<Pair<Int, Record<*, *>>>) = throw NotImplementedError()
}

/**
 * A [DBUtils] that tracks [persistedConfig] and [persistedConfigAudit].
 *
 * Throws on write if [writeFails]. Throws on read if [readFails].
 */
internal class DummyDBUtils(
    initialConfig: Map<String, ConfigEntity> = emptyMap(),
    private val writeFails: Boolean = false,
    private val readFails: Boolean = false
) : DBUtils {
    val persistedConfig = initialConfig.toMutableMap()
    val persistedConfigAudit
        get() = persistedConfig.values.map { config ->
            ConfigAuditEntity(config.section, config.config, config.configVersion, config.updateActor)
        }

    override fun writeEntities(newConfig: ConfigEntity, newConfigAudit: ConfigAuditEntity) {
        if (writeFails) {
            throw RollbackException()
        } else {
            this.persistedConfig[newConfig.section] = newConfig
        }
    }

    override fun readConfigEntity(section: String) = if (readFails) {
        throw IllegalStateException()
    } else {
        this.persistedConfig[section]
    }
}