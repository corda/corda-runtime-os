package net.corda.services.token.impl

import net.corda.data.services.TokenSetKey
import net.corda.data.services.TokenState
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.services.token.ClaimTimeoutScheduler
import net.corda.services.token.TokenRecordFactory
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import kotlin.math.min
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Component(service = [ClaimTimeoutSchedulerImpl::class])
class ClaimTimeoutSchedulerImpl constructor(
    private val publisherFactory: PublisherFactory,
    private val tokenRecordFactory: TokenRecordFactory,
    private val scheduledExecutorService: ScheduledExecutorService,
    private val clock: Clock
) : ClaimTimeoutScheduler {

    @Activate
    constructor(
        @Reference(service = PublisherFactory::class)
        publisherFactory: PublisherFactory,
        @Reference(service = TokenRecordFactory::class)
        tokenRecordFactory: TokenRecordFactory,
    ) : this(publisherFactory, tokenRecordFactory, Executors.newSingleThreadScheduledExecutor(), UTCClock())

    private val scheduledTimeoutCheckEvents = ConcurrentHashMap<TokenSetKey, ScheduledFuture<*>>()
    private var publisher: Publisher? = null

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        publisher?.close()
        publisher = publisherFactory.createPublisher(
            PublisherConfig("ClaimTimeoutScheduler"),
            config.getConfig(ConfigKeys.MESSAGING_CONFIG)
        )
    }

    override fun onPartitionSynced(states: Map<TokenSetKey, TokenState>) {
        states.forEach { scheduleClosestTimeout(it.key, it.value) }
    }

    override fun onPartitionLost(states: Map<TokenSetKey, TokenState>) {
        states.keys.forEach { scheduleDelete(it) }
    }

    override fun onPostCommit(updatedStates: Map<TokenSetKey, TokenState?>) {
        updatedStates.forEach {
            if (it.value == null) {
                scheduleDelete(it.key)
            } else {
                scheduleClosestTimeout(it.key, it.value!!)
            }
        }
    }

    private fun scheduleDelete(key: TokenSetKey) {
        scheduledTimeoutCheckEvents[key]?.cancel(false)
    }

    private fun scheduleClosestTimeout(key: TokenSetKey, state: TokenState) {
        val minExpiryTime = (state.blockedClaimQueries
            .filter { it.awaitExpiryTime != null }
            .map { it.awaitExpiryTime }
                +
                state.claimedTokens
                    .filter { it.claimExpiryTime != null }
                    .map { it.claimExpiryTime }
                ).minOfOrNull { it }

        scheduledTimeoutCheckEvents[key]?.cancel(false)

        if (minExpiryTime != null) {
            val minMsToNextCheck = min(0, minExpiryTime.toEpochMilli() - clock.instant().toEpochMilli())
            val closestTimeoutCheck = scheduledExecutorService.schedule(
                { publishTimeoutCheck(key) },
                minMsToNextCheck,
                TimeUnit.MILLISECONDS
            )
            scheduledTimeoutCheckEvents[key] = closestTimeoutCheck
        }
    }

    private fun publishTimeoutCheck(key: TokenSetKey) {
        publisher?.publish(listOf(tokenRecordFactory.createTimeout(key)))
    }
}