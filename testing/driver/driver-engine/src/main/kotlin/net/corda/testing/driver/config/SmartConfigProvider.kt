package net.corda.testing.driver.config

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.FlowConfig.EXTERNAL_EVENT_MAX_RETRIES
import net.corda.schema.configuration.FlowConfig.EXTERNAL_EVENT_MESSAGE_RESEND_WINDOW
import net.corda.schema.configuration.FlowConfig.PROCESSING_FLOW_CLEANUP_TIME
import net.corda.schema.configuration.FlowConfig.PROCESSING_MAX_FLOW_SLEEP_DURATION
import net.corda.schema.configuration.FlowConfig.PROCESSING_MAX_RETRY_ATTEMPTS
import net.corda.schema.configuration.FlowConfig.PROCESSING_MAX_RETRY_DELAY
import net.corda.schema.configuration.FlowConfig.SESSION_FLOW_CLEANUP_TIME
import net.corda.schema.configuration.FlowConfig.SESSION_HEARTBEAT_TIMEOUT_WINDOW
import net.corda.schema.configuration.FlowConfig.SESSION_MESSAGE_RESEND_WINDOW
import net.corda.schema.configuration.FlowConfig.SESSION_P2P_TTL
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import net.corda.schema.configuration.MessagingConfig.Subscription.PROCESSOR_TIMEOUT
import org.osgi.service.component.annotations.Component

@Component(service = [ SmartConfigProvider::class ])
class SmartConfigProvider {
    val smartConfig: SmartConfig

    init {
        val configFactory = SmartConfigFactory.createWithoutSecurityServices()

        val config = ConfigFactory.empty()
            .withValue(EXTERNAL_EVENT_MAX_RETRIES, ConfigValueFactory.fromAnyRef(0))
            .withValue(EXTERNAL_EVENT_MESSAGE_RESEND_WINDOW, ConfigValueFactory.fromAnyRef(5000L))
            .withValue(MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(Int.MAX_VALUE))
            .withValue(PROCESSING_FLOW_CLEANUP_TIME, ConfigValueFactory.fromAnyRef(5000L))
            .withValue(PROCESSING_MAX_RETRY_ATTEMPTS, ConfigValueFactory.fromAnyRef(5))
            .withValue(PROCESSING_MAX_RETRY_DELAY, ConfigValueFactory.fromAnyRef(5000L))
            .withValue(PROCESSING_MAX_FLOW_SLEEP_DURATION, ConfigValueFactory.fromAnyRef(5000L))
            .withValue(PROCESSOR_TIMEOUT, ConfigValueFactory.fromAnyRef(60000L))
            .withValue(SESSION_MESSAGE_RESEND_WINDOW, ConfigValueFactory.fromAnyRef(500000L))
            .withValue(SESSION_HEARTBEAT_TIMEOUT_WINDOW, ConfigValueFactory.fromAnyRef(500000L))
            .withValue(SESSION_FLOW_CLEANUP_TIME, ConfigValueFactory.fromAnyRef(5000L))
            .withValue(SESSION_P2P_TTL, ConfigValueFactory.fromAnyRef(5000L))
            .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(1))
        smartConfig = configFactory.create(config)
    }
}
