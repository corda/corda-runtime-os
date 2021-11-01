package net.corda.cpi.write.impl.kafka.factory

import com.typesafe.config.Config
import net.corda.cpi.read.factory.CPIReadFactory
import net.corda.cpi.write.CPIWrite
import net.corda.cpi.write.factory.CPIWriteFactory
import net.corda.cpi.write.impl.kafka.CPIWriteImplKafka
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CPIWriteFactory::class], property = ["type=kafka"])
class CPIWriteFactoryImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = CPIReadFactory::class, target = "(type=file)")
    private val readFactory: CPIReadFactory
) : CPIWriteFactory {
    override fun createCPIWrite(nodeConfig: Config): CPIWrite {
        return CPIWriteImplKafka(subscriptionFactory, publisherFactory, nodeConfig, readFactory)
    }
}