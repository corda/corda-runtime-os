package net.corda.cpk.write.impl

import net.corda.chunking.ChunkWriterFactory
import net.corda.chunking.toAvro
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpk.write.CpkWriteService
import net.corda.cpk.write.impl.services.db.CpkStorage
import net.corda.cpk.write.impl.services.db.impl.DBCpkStorage
import net.corda.cpk.write.impl.services.kafka.CpkChecksumsCache
import net.corda.cpk.write.impl.services.kafka.CpkChunksPublisher
import net.corda.cpk.write.impl.services.kafka.impl.CpkChecksumsCacheImpl
import net.corda.cpk.write.impl.services.kafka.impl.KafkaCpkChunksPublisher
import net.corda.data.chunking.CpkChunkId
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.cpi.datamodel.CpkFile
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.TimerEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.VirtualNode
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.RECONCILIATION_CONFIG
import net.corda.schema.configuration.MessagingConfig
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_CPK_WRITE_INTERVAL_MS
import net.corda.utilities.VisibleForTesting
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.seconds
import net.corda.v5.base.util.trace
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.time.Duration

// TODO at some later point consider deleting CPKs blobs in the database by nulling their blob values and pass the null value to Kafka
@Suppress("TooManyFunctions")
@Component(service = [CpkWriteService::class])
class CpkWriteServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager
) : CpkWriteService, LifecycleEventHandler {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        const val CPK_WRITE_GROUP = "cpk.writer"
        const val CPK_WRITE_CLIENT = "$CPK_WRITE_GROUP.client"
    }

    private val coordinator = coordinatorFactory.createCoordinator<CpkWriteService>(this)

    @VisibleForTesting
    internal var configReadServiceRegistration: RegistrationHandle? = null
    @VisibleForTesting
    internal var configSubscription: AutoCloseable? = null

    @VisibleForTesting
    internal var timeout: Duration? = null
    @VisibleForTesting
    internal var timerEventIntervalMs: Long? = null
    @VisibleForTesting
    internal var cpkChecksumsCache: CpkChecksumsCache? = null
    @VisibleForTesting
    internal var cpkChunksPublisher: CpkChunksPublisher? = null
    @VisibleForTesting
    internal var cpkStorage: CpkStorage? = null

    private var maxAllowedKafkaMsgSize: Int? = null

    private val timerKey = CpkWriteServiceImpl::class.simpleName!!

    /**
     * Event loop
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is ConfigChangedEvent -> onConfigChangedEvent(event, coordinator)
            is ReconcileCpkEvent -> onReconcileCpkEvent(coordinator)
            is StopEvent -> onStopEvent(coordinator)
        }
    }

    /**
     * We depend on the [ConfigurationReadService] so we 'listen' to [RegistrationStatusChangeEvent]
     * to tell us when it is ready so we can register ourselves to handle config updates.
     */
    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        configReadServiceRegistration?.close()
        configReadServiceRegistration =
            coordinator.followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                    LifecycleCoordinatorName.forComponent<DbConnectionManager>()
                )
            )
    }

    /**
     * If the thing(s) we depend on are up (only the [ConfigurationReadService]),
     * then register `this` for config updates
     */
    private fun onRegistrationStatusChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        if (event.status == LifecycleStatus.UP) {
            configSubscription = configReadService.registerComponentForUpdates(
                coordinator,
                setOf(
                    BOOT_CONFIG,
                    MESSAGING_CONFIG,
                    RECONCILIATION_CONFIG,
                )
            )
        } else {
            logger.warn(
                "Received a ${RegistrationStatusChangeEvent::class.java.simpleName} with status ${event.status}." +
                        " Component ${this::class.java.simpleName} is not started"
            )
            closeResources()
        }
    }

    /**
     * We've received a config event that we care about, we can now write cpks
     */
    private fun onConfigChangedEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
        val reconciliationConfig = event.config.getConfig(RECONCILIATION_CONFIG)
        maxAllowedKafkaMsgSize = messagingConfig.getInt(MessagingConfig.MAX_ALLOWED_MSG_SIZE)
        timeout = 20.seconds

        timerEventIntervalMs = reconciliationConfig.getLong(RECONCILIATION_CPK_WRITE_INTERVAL_MS)

        logger.info("CPK write reconciliation interval set to $timerEventIntervalMs ms.")

        try {
            createCpkChunksPublisher(messagingConfig)
        } catch (e: Exception) {
            closeResources()
            coordinator.updateStatus(LifecycleStatus.DOWN)
            return
        }
        createCpkChecksumsCache(messagingConfig)
        createCpkStorage()

        scheduleNextReconciliationTask(coordinator)
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun onReconcileCpkEvent(coordinator: LifecycleCoordinator) {
        try {
            putMissingCpk()
        } catch (e: Exception) {
            logger.warn("CPK Reconciliation exception: $e")
        }
        scheduleNextReconciliationTask(coordinator)
    }

    private fun scheduleNextReconciliationTask(coordinator: LifecycleCoordinator) {
        logger.trace { "Registering new ${ReconcileCpkEvent::class.simpleName}" }
        coordinator.setTimer(
            timerKey,
            timerEventIntervalMs!!
        ) { ReconcileCpkEvent(it) }
    }

    /**
     * Close the registration.
     */
    private fun onStopEvent(coordinator: LifecycleCoordinator) {
        coordinator.cancelTimer(timerKey)
        closeResources()
    }

    @VisibleForTesting
    internal fun putMissingCpk() {
        val cachedCpkIds = cpkChecksumsCache?.getCachedCpkIds() ?: run {
            logger.info("CPK Checksums Cache is not set yet, therefore will run a full db to kafka reconciliation")
            emptyList()
        }

        val cpkStorage =
            this.cpkStorage ?: throw CordaRuntimeException("CPK Storage Service is not set")
        val missingCpkIdsOnKafka = cpkStorage.getCpkIdsNotIn(cachedCpkIds)

        // Make sure we use the same CPK publisher for all CPK publishing.
        this.cpkChunksPublisher?.let {
            missingCpkIdsOnKafka
                .forEach { cpkChecksum ->
                    // TODO probably replace the following logging with debug
                    logger.info("Putting missing CPK to Kafka: $cpkChecksum")
                    val cpkFile = cpkStorage.getCpkFileById(cpkChecksum)
                    it.chunkAndPublishCpk(cpkFile)
                }
        } ?: throw CordaRuntimeException("CPK Chunks Publisher service is not set")
    }

    private fun CpkChunksPublisher.chunkAndPublishCpk(cpkFile: CpkFile) {
        logger.debug { "Publishing CPK ${cpkFile.fileChecksum}" }
        val cpkChecksum = cpkFile.fileChecksum
        val cpkData = cpkFile.data
        val chunkWriter = maxAllowedKafkaMsgSize?.let {
            ChunkWriterFactory.create(it)
        } ?: throw CordaRuntimeException("maxAllowedKafkaMsgSize is not set")

        chunkWriter.onChunk { chunk ->
            val cpkChunkId = CpkChunkId(cpkChecksum.toAvro(), chunk.partNumber)
            put(cpkChunkId, chunk)
        }
        chunkWriter.write(cpkChecksum.toFileName(), ByteArrayInputStream(cpkData))
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.debug("CPK Write Service starting")
        coordinator.start()
    }

    override fun stop() {
        logger.debug("CPK Write Service stopping")
        coordinator.stop()
        closeResources()
    }

    private fun closeResources() {
        configReadServiceRegistration?.close()
        configReadServiceRegistration = null
        configSubscription?.close()
        configSubscription = null
        cpkChecksumsCache?.close()
        cpkChecksumsCache = null
        cpkChunksPublisher?.close()
        cpkChunksPublisher = null
    }

    private fun createCpkChecksumsCache(config: SmartConfig) {
        cpkChecksumsCache?.close()
        cpkChecksumsCache = CpkChecksumsCacheImpl(
            subscriptionFactory,
            SubscriptionConfig(CPK_WRITE_GROUP, VirtualNode.CPK_FILE_TOPIC),
            config
        ).also { it.start() }
    }

    private fun createCpkChunksPublisher(config: SmartConfig) {
        cpkChunksPublisher?.close()
        val publisher = publisherFactory.createPublisher(
            PublisherConfig(CPK_WRITE_CLIENT),
            config
        ).also { it.start() }
        cpkChunksPublisher = KafkaCpkChunksPublisher(publisher, timeout!!, VirtualNode.CPK_FILE_TOPIC)
    }

    private fun createCpkStorage() {
        cpkStorage = DBCpkStorage(dbConnectionManager.getClusterEntityManagerFactory())
    }

    private data class ReconcileCpkEvent(override val key: String): TimerEvent
}

// Must not call SecureHash.toString() because it contains delimiter : that fails on Path creation.
// Therefore the file name will be the <hex string>.cpk.
private fun SecureHash.toFileName() = "${this.toHexString()}.cpk"
