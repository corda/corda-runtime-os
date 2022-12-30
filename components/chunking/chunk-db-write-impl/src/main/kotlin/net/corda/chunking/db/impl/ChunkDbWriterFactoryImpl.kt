package net.corda.chunking.db.impl

import net.corda.chunking.RequestId
import net.corda.chunking.db.ChunkDbWriter
import net.corda.chunking.db.ChunkDbWriterFactory
import net.corda.chunking.db.ChunkWriteException
import net.corda.chunking.db.impl.persistence.StatusPublisher
import net.corda.chunking.db.impl.persistence.database.DatabaseChunkPersistence
import net.corda.chunking.db.impl.persistence.database.DatabaseCpiPersistence
import net.corda.chunking.db.impl.validation.CpiValidatorImpl
import net.corda.configuration.read.ConfigurationGetService
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.data.chunking.Chunk
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.certificate.service.CertificatesService
import net.corda.membership.lib.schema.validation.MembershipSchemaValidatorFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.utilities.PathProvider
import net.corda.utilities.TempPathProvider
import net.corda.utilities.time.UTCClock
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import javax.persistence.EntityManagerFactory

@Suppress("UNUSED", "LongParameterList")
@Component(service = [ChunkDbWriterFactory::class])
class ChunkDbWriterFactoryImpl(
    private val subscriptionFactory: SubscriptionFactory,
    private val publisherFactory: PublisherFactory,
    private val tempPathProvider: PathProvider,
    private val certificatesService: CertificatesService,
    private val membershipSchemaValidatorFactory: MembershipSchemaValidatorFactory,
    private val configurationGetService: ConfigurationGetService,
) : ChunkDbWriterFactory {

    @Activate
    constructor(
        @Reference(service = SubscriptionFactory::class)
        subscriptionFactory: SubscriptionFactory,
        @Reference(service = PublisherFactory::class)
        publisherFactory: PublisherFactory,
        @Reference(service = CertificatesService::class)
        certificatesService: CertificatesService,
        @Reference(service = MembershipSchemaValidatorFactory::class)
        membershipSchemaValidatorFactory: MembershipSchemaValidatorFactory,
        @Reference(service = ConfigurationGetService::class)
        configurationGetService: ConfigurationGetService,
    ) : this(
        subscriptionFactory,
        publisherFactory,
        TempPathProvider(),
        certificatesService,
        membershipSchemaValidatorFactory,
        configurationGetService,
    )

    companion object {
        internal const val GROUP_NAME = "cpi.chunk.writer"
        internal const val CLIENT_NAME = "chunk-writer"

        const val CPI_CACHE_DIR = "cpi-cache"
        const val CPI_PARTS_DIR = "cpi-parts"
    }

    override fun create(
        messagingConfig: SmartConfig,
        bootConfig: SmartConfig,
        entityManagerFactory: EntityManagerFactory,
        cpiInfoWriteService: CpiInfoWriteService
    ): ChunkDbWriter {
        // Could be reused
        val uploadTopic = Schemas.VirtualNode.CPI_UPLOAD_TOPIC
        val statusTopic = Schemas.VirtualNode.CPI_UPLOAD_STATUS_TOPIC

        // Must hang on to a reference to the publisher that is created here such that it can be closed in the event
        // that the subscription is closed.
        val (publisher, subscription) = createSubscription(
            uploadTopic,
            messagingConfig,
            bootConfig,
            entityManagerFactory,
            statusTopic,
            cpiInfoWriteService
        )

        return ChunkDbWriterImpl(subscription, publisher)
    }

    private fun createPublisher(config: SmartConfig): Publisher {
        val publisherConfig = PublisherConfig(CLIENT_NAME)
        return publisherFactory.createPublisher(publisherConfig, config)
    }

    /**
     * @param uploadTopic we read (subscribe) chunks from this topic
     * @param messagingConfig
     * @param entityManagerFactory we use this to write chunks to the database
     * @param statusTopic we write (publish) status changes to this topic
     */
    @Suppress("LongParameterList")
    private fun createSubscription(
        uploadTopic: String,
        messagingConfig: SmartConfig,
        bootConfig: SmartConfig,
        entityManagerFactory: EntityManagerFactory,
        statusTopic: String,
        cpiInfoWriteService: CpiInfoWriteService
    ): Pair<Publisher, Subscription<RequestId, Chunk>> {
        val chunkPersistence = DatabaseChunkPersistence(entityManagerFactory)
        val cpiPersistence = DatabaseCpiPersistence(entityManagerFactory)
        val publisher = createPublisher(messagingConfig)
        val statusPublisher = StatusPublisher(statusTopic, publisher)
        val cpiCacheDir = tempPathProvider.getOrCreate(bootConfig, CPI_CACHE_DIR)
        val cpiPartsDir = tempPathProvider.getOrCreate(bootConfig, CPI_PARTS_DIR)
        val membershipSchemaValidator = membershipSchemaValidatorFactory.createValidator()
        val validator = CpiValidatorImpl(
            statusPublisher,
            chunkPersistence,
            cpiPersistence,
            cpiInfoWriteService,
            membershipSchemaValidator,
            configurationGetService,
            cpiCacheDir,
            cpiPartsDir,
            certificatesService,
            UTCClock()
        )
        val processor = ChunkWriteToDbProcessor(statusPublisher, chunkPersistence, validator)
        val subscriptionConfig = SubscriptionConfig(GROUP_NAME, uploadTopic)
        return try {
            Pair(
                publisher,
                subscriptionFactory.createDurableSubscription(subscriptionConfig, processor, messagingConfig, null)
            )
        } catch (e: Exception) {
            // If a failure happens such that the subscription could not be created, the publisher we've just created
            // needs to be deleted.
            publisher.close()
            throw ChunkWriteException("Could not create subscription to process configuration update requests.", e)
        }
    }
}
