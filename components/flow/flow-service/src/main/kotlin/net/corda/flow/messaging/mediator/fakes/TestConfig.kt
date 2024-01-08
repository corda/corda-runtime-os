package net.corda.flow.messaging.mediator.fakes

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.FlowConfig
import net.corda.schema.configuration.MessagingConfig

data class TestConfig(
    private val processorTimeout: Long = 15_000,
    private val maxRetryAttempts: Long = 5,
    private val maxRetryWindowDuration: Long = 300_000,
    private val processingFlowCleanupTime: Long = 5_000,
    private val maxAllowedMessageSize: Long = 100_000_000,
    private val threadPoolSize: Long = 8,
    private val minPoolRecordCount: Long = 20,
    private val pollTimeout: Long = 50,
    private val pollRecords: Long = 20,
    private val pollTimeMs: Long = 3,
    private val commitTimeMs: Long = 3,
    private val resetOffsetTimeMs: Long = 3,
    private val sendTimeMs: Long = 3,
    private val rpcSendTimeMs: Long = 8,
) {
    fun toSmartConfigs(): Map<String, SmartConfig> {
        val flowConfig = SmartConfigImpl.empty()
            .withValue(MessagingConfig.Subscription.PROCESSOR_TIMEOUT, fromAnyRef(processorTimeout))
            .withValue(FlowConfig.PROCESSING_MAX_RETRY_ATTEMPTS, fromAnyRef(maxRetryAttempts))
            .withValue(FlowConfig.PROCESSING_MAX_RETRY_WINDOW_DURATION, fromAnyRef(maxRetryWindowDuration))
            .withValue(FlowConfig.PROCESSING_FLOW_CLEANUP_TIME, fromAnyRef(processingFlowCleanupTime))
        val messagingConfig = SmartConfigImpl.empty()
            .withValue(MessagingConfig.MAX_ALLOWED_MSG_SIZE, fromAnyRef(maxAllowedMessageSize))
            .withValue(MessagingConfig.Subscription.MEDIATOR_PROCESSING_THREAD_POOL_SIZE, fromAnyRef(threadPoolSize))
            .withValue(MessagingConfig.Subscription.MEDIATOR_PROCESSING_MIN_POOL_RECORD_COUNT, fromAnyRef(minPoolRecordCount))
            // Message Bus Consumer
            .withValue(MessagingConfig.Subscription.MEDIATOR_PROCESSING_POLL_TIMEOUT, fromAnyRef(pollTimeout))
            .withValue(TestMediatorConsumerFactoryFactory.POLL_RECORDS, fromAnyRef(pollRecords))
            .withValue(TestMediatorConsumerFactoryFactory.POLL_TIME_MS, fromAnyRef(pollTimeMs))
            .withValue(TestMediatorConsumerFactoryFactory.COMMIT_TIME_MS, fromAnyRef(commitTimeMs))
            .withValue(TestMediatorConsumerFactoryFactory.RESET_OFFSET_TIME_MS, fromAnyRef(resetOffsetTimeMs))
            // Message Bus Client
            .withValue(TestMessagingClientFactoryFactory.SEND_TIME_MS, fromAnyRef(sendTimeMs))
            // RPC Client
            .withValue(TestRpcClientFactory.RPC_SEND_TIME_MS, fromAnyRef(rpcSendTimeMs))

        return mapOf(
            ConfigKeys.FLOW_CONFIG to flowConfig,
            ConfigKeys.MESSAGING_CONFIG to messagingConfig
        )
    }
}