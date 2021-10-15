package net.corda.crypto.service.config

import com.typesafe.config.ConfigFactory
import net.corda.crypto.impl.closeGracefully
import net.corda.crypto.impl.config.CryptoConfigMap
import net.corda.crypto.impl.config.memberConfig
import net.corda.data.crypto.config.CryptoConfigurationRecord
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig.Companion.DEFAULT_MEMBER_KEY
import net.corda.v5.cipher.suite.config.CryptoMemberConfig
import net.corda.v5.cipher.suite.lifecycle.CryptoLifecycleComponent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

@Component(service = [MemberConfigReader::class])
class MemberConfigReaderImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
) : MemberConfigReader, Lifecycle, CryptoLifecycleComponent {
    companion object {
        private val logger: Logger = contextLogger()
    }

    private var impl = Impl(
        subscriptionFactory,
        CryptoConfigMap(emptyMap()),
        logger
    )

    override var isRunning: Boolean = false

    override fun start() {
        logger.info("Starting...")
        isRunning = true
    }

    override fun stop() {
        logger.info("Stopping...")
        impl.closeGracefully()
        isRunning = false
    }

    override fun handleConfigEvent(config: CryptoLibraryConfig) {
        logger.info("Received new configuration...")
        val currentImpl = impl
        impl = Impl(
            subscriptionFactory,
            config.memberConfig,
            logger
        )
        currentImpl.closeGracefully()
    }

    override fun get(memberId: String): CryptoMemberConfig = impl.get(memberId)

    private class Impl(
        subscriptionFactory: SubscriptionFactory,
        config: CryptoConfigMap,
        private val logger: Logger
    ) : CompactedProcessor<String, CryptoConfigurationRecord>, AutoCloseable {
        companion object {
            private val DEFAULT_MEMBER_CONFIG = CryptoMemberConfigImpl(emptyMap())
        }

        private val groupName: String = config.getString(ConfigConsts.GROUP_NAME_KEY)

        private val topicName: String = config.getString(ConfigConsts.TOPIC_NAME_KEY)

        private var subscription: CompactedSubscription<String, CryptoConfigurationRecord>? =
            if(config.isEmpty()) {
                null
            } else {
                subscriptionFactory.createCompactedSubscription(
                    SubscriptionConfig(groupName, topicName),
                    this
                ).also { it.start() }
            }

        private var configMap = ConcurrentHashMap<String, CryptoMemberConfig>()

        fun get(memberId: String): CryptoMemberConfig {
            logger.debug("Getting configuration for member {}", memberId)
            return configMap[memberId] ?: configMap[DEFAULT_MEMBER_KEY] ?: DEFAULT_MEMBER_CONFIG
        }

        override val keyClass: Class<String> = String::class.java

        override val valueClass: Class<CryptoConfigurationRecord> = CryptoConfigurationRecord::class.java

        override fun onSnapshot(currentData: Map<String, CryptoConfigurationRecord>) {
            logger.debug("Processing snapshot of {} items", currentData.size)
            val map = ConcurrentHashMap<String, CryptoMemberConfig>()
            for (record in currentData) {
                map[record.key] = toMemberConfig(record.value)
            }
            configMap = map
        }

        override fun onNext(
            newRecord: Record<String, CryptoConfigurationRecord>,
            oldValue: CryptoConfigurationRecord?,
            currentData: Map<String, CryptoConfigurationRecord>
        ) {
            logger.debug("Processing new update")
            if(newRecord.value == null) {
                configMap.remove(newRecord.key)
            } else {
                configMap[newRecord.key] = toMemberConfig(newRecord.value!!)
            }
        }

        override fun close() {
            subscription?.closeGracefully()
        }

        private fun toMemberConfig(value: CryptoConfigurationRecord) =
            CryptoMemberConfigImpl(
                if (value.value.isNullOrEmpty()) {
                    emptyMap()
                } else {
                    ConfigFactory.parseString(value.value).root().unwrapped()
                }
            )
    }
}