package net.corda.tools.kafka


import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.osgi.api.Application
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(immediate = true)
class App @Activate constructor(
    @Reference(service = KafkaTopicAdmin::class)
    private var kafkaTopicAdmin: KafkaTopicAdmin,
) : Application, BundleActivator {

    var context: BundleContext? = null

    @Activate
    override fun start(context: BundleContext?) {
        println("start")
        if (context != null) {
            this.context = context
        }
    }

    override fun stop(context: BundleContext?) {
    }

    override fun startup(args: Array<String>) {
        println("startup")
        if (context != null) {
            val serviceReference = context!!.getServiceReference(KafkaTopicAdmin::class.java)
            if (serviceReference != null) {
                println("service reference: $serviceReference")
                println("service: $kafkaTopicAdmin")
            }
        }
    }

    override fun shutdown() {
        println("shutdown")
    }
}