package net.corda.virtualnode.rpcops.common.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.rpcops.common.RPCSenderFactory
import net.corda.virtualnode.rpcops.common.RPCSenderWrapper
import net.corda.virtualnode.rpcops.common.SENDER_CONFIG
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
