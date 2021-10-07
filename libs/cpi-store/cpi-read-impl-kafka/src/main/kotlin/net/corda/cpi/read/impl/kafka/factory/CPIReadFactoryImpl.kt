package net.corda.cpi.read.impl.kafka.factory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory
import net.corda.cpi.read.CPIRead
import net.corda.cpi.read.factory.CPIReadFactory
import net.corda.cpi.read.impl.kafka.CPIReadImplKafka
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(immediate = true, service = [CPIReadFactory::class])
class CPIReadFactoryImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory
) : CPIReadFactory {
    override fun createCPIRead(nodeConfig: Config): CPIRead {
        return CPIReadImplKafka(subscriptionFactory, nodeConfig)
    }
}
