package net.corda.cpk.write.impl

import net.corda.chunking.ChunkWriterFactory
import net.corda.chunking.ChunkWriterFactory.SUGGESTED_CHUNK_SIZE
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpk.write.CpkWriteService
import net.corda.cpk.write.impl.services.db.CpkChecksumToData
import net.corda.cpk.write.impl.services.db.CpkStorage
import net.corda.cpk.write.impl.services.db.impl.DBCpkStorage
import net.corda.cpk.write.impl.services.kafka.AvroTypesTodo
import net.corda.cpk.write.impl.services.kafka.CpkChecksumsCache
import net.corda.cpk.write.impl.services.kafka.CpkChunksPublisher
import net.corda.cpk.write.impl.services.kafka.impl.CpkChecksumsCacheImpl
import net.corda.cpk.write.impl.services.kafka.impl.KafkaCpkChunksPublisher
import net.corda.cpk.write.impl.services.kafka.toAvro
import net.corda.db.connection.manager.DbConnectionManager
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
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.seconds
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.io.ByteArrayInputStream
import java.nio.file.Paths
import java.time.Duration
import kotlin.concurrent.thread

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
        val logger: Logger = contextLogger()

        const val CPK_WRITE_TOPIC = Schemas.VirtualNode.CPK_FILE_TOPIC
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
    internal var cpkChecksumsCache: CpkChecksumsCache? = null
    @VisibleForTesting
    internal var cpkChunksPublisher: CpkChunksPublisher? = null
    @VisibleForTesting
    internal var cpkStorage: CpkStorage? = null

    /**
     * Event loop
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is ConfigChangedEvent -> onConfigChangedEvent(event, coordinator)
            is StopEvent -> onStopEvent()
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
                setOf(ConfigKeys.BOOT_CONFIG)
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
        val config = event.config[ConfigKeys.BOOT_CONFIG]!!
        // TODO - kyriakos - fix expected configuration and fill following properties with configuration
        //if (config.hasPath("todo")) {

            timeout = 20.seconds
            cpkChecksumsCache = CpkChecksumsCacheImpl(
                subscriptionFactory,
                SubscriptionConfig(CPK_WRITE_GROUP, CPK_WRITE_TOPIC),
                config
            ).also { it.start() }

            val publisher = publisherFactory.createPublisher(
                PublisherConfig(CPK_WRITE_CLIENT),
                config
            )
            cpkChunksPublisher = KafkaCpkChunksPublisher(publisher, timeout!!, CPK_WRITE_TOPIC)
            cpkStorage = DBCpkStorage(dbConnectionManager.clusterDbEntityManagerFactory)

            coordinator.updateStatus(LifecycleStatus.UP)
//        } else {
//            logger.warn(
//                "Need ${CpkServiceConfigKeys.CPK_CACHE_DIR} to be specified in the boot config." +
//                        " Component ${this::class.java.simpleName} is not started"
//            )
//            closeResources()
//        }
    }

    /**
     * Close the registration.
     */
    private fun onStopEvent() {
        closeResources()
    }

    // TODO - kyriakos - need to schedule this to run like a timer task
    override fun putMissingCpk() {
        val cachedCpkIds = cpkChecksumsCache?.getCachedCpkIds() ?: run {
            logger.info("cpkChecksumsCache is not set yet, therefore will run a full db to kafka reconciliation")
            emptySet()
        }

        val cpkStorage =
            this.cpkStorage ?: throw CordaRuntimeException("CPK storage service is not set")
        val missingCpkIdsOnKafka = cpkStorage.getCpkIdsNotIn(cachedCpkIds)

        missingCpkIdsOnKafka.forEach {
            // Make sure we use the same CPK publisher per CPK publish.
            val cpkChunksPublisher =
                this.cpkChunksPublisher ?: throw CordaRuntimeException("CPK chunks publisher service is not set")
            val cpkChecksumData = cpkStorage.getCpkDataByCpkId(it)
            cpkChunksPublisher.chunkAndPublishCpk(cpkChecksumData)
        }
    }

    private fun CpkChunksPublisher.chunkAndPublishCpk(cpkChecksumToData: CpkChecksumToData) {
        val cpkChecksum = cpkChecksumToData.checksum
        val cpkData = cpkChecksumToData.data
        val chunkWriter = ChunkWriterFactory.create(SUGGESTED_CHUNK_SIZE)
        chunkWriter.onChunk { chunk ->
            val cpkChunkId = AvroTypesTodo.CpkChunkIdAvro(cpkChecksum.toAvro(), chunk.partNumber)
            put(cpkChunkId, chunk)
        }
        chunkWriter.write(Paths.get("todo"), ByteArrayInputStream(cpkData))
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.debug { "Cpk Write Service starting" }
        coordinator.start()
    }

    override fun stop() {
        logger.debug { "Cpk Write Service stopping" }
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
        cpkChunksPublisher = null
    }
}
