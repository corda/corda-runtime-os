package net.corda.messaging.db.subscription

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.RandomAccessSubscription
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.db.persistence.DBAccessProvider
import net.corda.messaging.db.sync.OffsetTrackersManager
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import java.nio.ByteBuffer
import java.sql.SQLClientInfoException
import java.sql.SQLNonTransientException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Suppress("LongParameterList")
class DBRandomAccessSubscription<K: Any, V: Any>(private val subscriptionConfig: SubscriptionConfig,
                                                 private val avroSchemaRegistry: AvroSchemaRegistry,
                                                 private val offsetTrackersManager: OffsetTrackersManager,
                                                 private val dbAccessProvider: DBAccessProvider,
                                                 private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
                                                 private val keyClass: Class<K>,
                                                 private val valueClass: Class<V>): RandomAccessSubscription<K, V>, Lifecycle {

    companion object {
        private val log: Logger = contextLogger()
    }

    private var running = false
    private val startStopLock = ReentrantLock()
    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(
        LifecycleCoordinatorName(
            "${subscriptionConfig.groupName}-DBRandomAccessSubscription-${subscriptionConfig.eventTopic}",
            subscriptionConfig.instanceId.toString()
        )
    ) { _, _ -> }

    override fun start() {
        startStopLock.withLock {
            if (!running) {
                running = true
                lifecycleCoordinator.start()
                lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
                log.debug { "Random access subscription for topic ${subscriptionConfig.eventTopic} " +
                        "and group ${subscriptionConfig.groupName} started." }
            }
        }
    }

    override fun stop() {
        startStopLock.withLock {
            if (running) {
                running = false
                lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
                lifecycleCoordinator.stop()
                log.debug { "Random access subscription for topic ${subscriptionConfig.eventTopic} " +
                        "and group ${subscriptionConfig.groupName} stopped." }
            }
        }
    }

    override fun close() {
        startStopLock.withLock {
            if (running) {
                running = false
                lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
                lifecycleCoordinator.close()
                log.debug { "Random access subscription for topic ${subscriptionConfig.eventTopic} " +
                        "and group ${subscriptionConfig.groupName} closed." }
            }
        }
    }

    override val isRunning: Boolean
        get() = running

    override fun getRecord(partition: Int, offset: Long): Record<K, V>? {
        val maxVisibleOffset = offsetTrackersManager.maxVisibleOffset(subscriptionConfig.eventTopic, partition)

        if (offset > maxVisibleOffset) {
            return null
        } else {
            val dbRecord = try {
                dbAccessProvider.getRecord(subscriptionConfig.eventTopic, partition, offset)
            } catch (e: Exception) {
                val errorMessage = "Failed to retrieve record from topic ${subscriptionConfig.eventTopic} " +
                                            "at location (partition: $partition, offset: $offset)."
                log.error(errorMessage, e)
                when(e) {
                    is SQLNonTransientException, is SQLClientInfoException,  -> {
                        throw CordaMessageAPIFatalException(errorMessage, e)
                    }
                    else -> {
                        throw CordaMessageAPIIntermittentException(errorMessage, e)
                    }
                }
            }

            return if (dbRecord == null) {
                null
            } else {
                val deserialisedKey = avroSchemaRegistry.deserialize(ByteBuffer.wrap(dbRecord.key), keyClass, null)
                val deserialisedValue = if (dbRecord.value == null) {
                    null
                } else {
                    avroSchemaRegistry.deserialize(ByteBuffer.wrap(dbRecord.value), valueClass, null)
                }
                Record(subscriptionConfig.eventTopic, deserialisedKey, deserialisedValue)
            }
        }
    }

    override val subscriptionName: LifecycleCoordinatorName
        get() = lifecycleCoordinator.name

}