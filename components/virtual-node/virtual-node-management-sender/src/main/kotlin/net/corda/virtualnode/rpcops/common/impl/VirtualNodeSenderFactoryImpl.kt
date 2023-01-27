package net.corda.virtualnode.rpcops.common.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.rpcops.common.VirtualNodeSender
import net.corda.virtualnode.rpcops.common.VirtualNodeSenderFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Duration

@Component(service = [VirtualNodeSenderFactory::class])
class VirtualNodeSenderFactoryImpl @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
) : VirtualNodeSenderFactory {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * Provides an injectable factory to create [VirtualNodeSender]s.
     *
     * @property timeout is a [Duration]. Defines how long to wait before assuming something went wrong in a given request
     * @property messagingConfig is a [SmartConfig]. This is the config for the given RPCSender to be created
     * @property asyncPublisherConfig is a [PublisherConfig]. This is the config for the publishing for asynchronous requests
     * @throws CordaRuntimeException If the updated sender cannot not be created.
     * @return [VirtualNodeSender] is a wrapper object around a sender, and the accompanying timeout
     * @see VirtualNodeSender
     */
    override fun createSender(
        timeout: Duration, messagingConfig: SmartConfig, asyncPublisherConfig: PublisherConfig
    ): VirtualNodeSender {
        try {
            return VirtualNodeSenderImpl(
                timeout,
                publisherFactory.createRPCSender(SENDER_CONFIG, messagingConfig).apply {
                    start()
                },
                publisherFactory.createPublisher(asyncPublisherConfig, messagingConfig)
            )
        } catch (e: Exception) {
            logger.error("Exception was thrown while attempting to set up the sender or its timeout: $e")
            // Exception will implicitly perform coordinator.updateStatus(LifecycleStatus.ERROR)
            throw CordaRuntimeException(
                "Exception was thrown while attempting to set up the sender or its timeout",
                e
            )
        }
    }
}
