package net.corda.crypto.component.config

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.crypto.impl.closeGracefully
import net.corda.crypto.impl.config.CryptoConfigMap
import net.corda.crypto.impl.config.memberConfig
import net.corda.data.crypto.config.CryptoConfigurationRecord
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import net.corda.v5.cipher.suite.config.CryptoMemberConfig
import net.corda.v5.cipher.suite.lifecycle.CryptoLifecycleComponent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.time.Instant
import java.util.concurrent.CompletableFuture

@Component(service = [MemberConfigWriter::class])
class MemberConfigWriterImpl @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : MemberConfigWriter, Lifecycle, CryptoLifecycleComponent {
    private companion object {
        private val logger: Logger = contextLogger()
    }

    private var impl: Impl? = null

    override var isRunning: Boolean = false

    override fun start() {
        logger.info("Starting...")
        isRunning = true
    }

    override fun stop() {
        logger.info("Stopping...")
        impl?.closeGracefully()
        isRunning = false
    }

    override fun handleConfigEvent(config: CryptoLibraryConfig) {
        logger.info("Received new configuration...")
        val currentImpl = impl
        impl = Impl(
            publisherFactory,
            config.memberConfig,
            logger
        )
        currentImpl?.closeGracefully()
    }

    override fun put(memberId: String, entity: CryptoMemberConfig): CompletableFuture<Unit> =
        if(impl == null) {
            throw IllegalStateException("The writer has not been initialize yet.")
        } else {
            impl!!.put(memberId, entity)
        }

    private class Impl(
        publisherFactory: PublisherFactory,
        config: CryptoConfigMap,
        val logger: Logger
    ) : AutoCloseable {
        private val topicName: String = config.getString(ConfigConsts.TOPIC_NAME_KEY)

        private val clientId: String = config.getString(ConfigConsts.CLIENT_ID_KEY)

        private val pub: Publisher = publisherFactory.createPublisher(
            PublisherConfig(clientId)
        ).also { it.start() }

        override fun close() {
            pub.close()
        }

        fun put(memberId: String, entity: CryptoMemberConfig): CompletableFuture<Unit> {
            logger.info("Publishing a record '{}' for member key='{}'", entity::class.java.name, memberId)
            val record = toRecord(entity)
            return pub.publish(listOf(Record(topicName, memberId, record)))[0]
        }

        private fun toRecord(entity: CryptoMemberConfig): CryptoConfigurationRecord {
            return CryptoConfigurationRecord(
                ConfigFactory.parseMap(entity).root().render(ConfigRenderOptions.concise()),
                Instant.now(),
                1
            )
        }
    }
}