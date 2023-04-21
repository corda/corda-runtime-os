package net.corda.messagebus.kafka.admin.builder

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.admin.Admin
import net.corda.messagebus.api.admin.builder.AdminBuilder
import net.corda.messagebus.api.configuration.AdminConfig
import net.corda.messagebus.kafka.admin.KafkaAdmin
import net.corda.messagebus.kafka.config.MessageBusConfigResolver
import net.corda.utilities.classload.executeWithThreadContextClassLoader
import org.apache.kafka.clients.admin.AdminClient
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.wiring.BundleWiring
import org.osgi.service.component.annotations.Component

@Suppress("Unused")
@Component(service = [AdminBuilder::class])
class KafkaAdminBuilder : AdminBuilder {
    override fun createAdmin(adminConfig: AdminConfig, messageBusConfig: SmartConfig): Admin {
        val configResolver = MessageBusConfigResolver(messageBusConfig.factory)
        val kafkaProperties = configResolver.resolve(messageBusConfig, adminConfig)

        val currentBundle = FrameworkUtil.getBundle(AdminClient::class.java)

        return if (currentBundle != null) {
            executeWithThreadContextClassLoader(currentBundle.adapt(BundleWiring::class.java).classLoader) {
                KafkaAdmin(AdminClient.create(kafkaProperties))
            }
        } else {
            KafkaAdmin(AdminClient.create(kafkaProperties))
        }
    }
}
