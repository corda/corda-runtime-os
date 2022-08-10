package net.corda.virtualnode.rpcops.common.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.rpcops.common.RPCSenderFactory
import net.corda.virtualnode.rpcops.common.RPCSenderWrapper
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration

@Component(service = [RPCSenderFactory::class])
class RPCSenderFactoryImpl @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
) : RPCSenderFactory {
    companion object {
        private val logger = contextLogger()
    }

    /**
     * Sends the [request] to the configuration management topic on bus.
     *
     * @property timeout is a [Duration]. This acts as a timeout for how long to wait before assuming something went
     *  wrong in a given request
     * @property messagingConfig is a [SmartConfig]. This is the config for the given RPCSender to be created
     * @throws CordaRuntimeException If the updated sender cannot not be created.
     * @return [RPCSenderWrapper] is a wrapper object around a sender, and the accompanying timeout
     * @see RPCSenderWrapper
     */
    override fun createSender(timeout: Duration, messagingConfig: SmartConfig): RPCSenderWrapper {
        try {
            return RPCSenderWrapperImpl(
                timeout,
                publisherFactory.createRPCSender(SENDER_CONFIG, messagingConfig).apply {
                    start()
                }
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
