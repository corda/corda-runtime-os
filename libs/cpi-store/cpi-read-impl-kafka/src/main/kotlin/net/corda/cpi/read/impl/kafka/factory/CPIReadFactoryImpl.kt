package net.corda.cpi.read.impl.kafka.factory;

import net.corda.cpi.read.CPIRead
import net.corda.cpi.read.CPISegmentReader
import net.corda.cpi.read.factory.CPIReadFactory
import net.corda.cpi.read.impl.kafka.CPIReadImplKafka
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CPIReadFactory::class], property = ["type=kafka"])
class CPIReadFactoryImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : CPIReadFactory {
    override fun createCPIRead(nodeConfig: SmartConfig): CPIRead {
        return CPIReadImplKafka(subscriptionFactory, publisherFactory, nodeConfig)
    }

    override fun createCPIReadSegment(nodeConfig: SmartConfig): CPISegmentReader {
        throw NotImplementedError("createCPIReadSegment not implemented for Kafka reader")
    }
}
