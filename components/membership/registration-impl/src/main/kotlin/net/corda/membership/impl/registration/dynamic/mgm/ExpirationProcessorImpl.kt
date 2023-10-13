package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.core.ShortHash
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.common.v2.RegistrationStatus.PENDING_MEMBER_VERIFICATION
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.TimerEvent
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.registration.DECLINED_REASON_FOR_USER_INTERNAL_ERROR
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.ExpirationProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MEMBERSHIP_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MembershipConfig.EXPIRATION_DATE_FOR_REGISTRATION_REQUESTS
import net.corda.schema.configuration.MembershipConfig.MAX_DURATION_BETWEEN_EXPIRED_REGISTRATION_REQUESTS_POLLS
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Component(service = [ExpirationProcessor::class])
@Suppress("LongParameterList")
internal class ExpirationProcessorImpl internal constructor(
    private val publisherFactory: PublisherFactory,
    private val configurationReadService: ConfigurationReadService,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val membershipQueryClient: MembershipQueryClient,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val clock: Clock,
) : ExpirationProcessor {
    @Activate
    constructor(
        @Reference(service = PublisherFactory::class)
        publisherFactory: PublisherFactory,
        @Reference(service = ConfigurationReadService::class)
        configurationReadService: ConfigurationReadService,
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = MembershipQueryClient::class)
        membershipQueryClient: MembershipQueryClient,
        @Reference(service = VirtualNodeInfoReadService::class)
        virtualNodeInfoReadService: VirtualNodeInfoReadService,
        @Reference(service = MembershipGroupReaderProvider::class)
        membershipGroupReaderProvider: MembershipGroupReaderProvider,
    ) : this (
        publisherFactory,
        configurationReadService,
        coordinatorFactory,
        membershipQueryClient,
        virtualNodeInfoReadService,
        membershipGroupReaderProvider,
        UTCClock(),
    )
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        const val SERVICE = "ExpirationProcessor"
        const val FOLLOW_CHANGES_RESOURCE_NAME = "ExpirationProcessor.followStatusChangesByName"
        const val WAIT_FOR_CONFIG_RESOURCE_NAME = "ExpirationProcessor.registerComponentForUpdates"
        const val PUBLISHER_RESOURCE_NAME = "ExpirationProcessor.publisher"
        const val PUBLISHER_CLIENT_ID = "expiration-processor"
    }

    private val coordinatorName = LifecycleCoordinatorName.forComponent<ExpirationProcessor>()
    // Component lifecycle coordinator
    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, ::handleEvent)

    private var impl: InnerExpirationProcessor = InactiveImpl()

    private val mgms = ConcurrentHashMap.newKeySet<HoldingIdentity>()

    override val isRunning: Boolean
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun scheduleProcessingOfExpiredRequests(mgm: HoldingIdentity) {
        impl.cancelOrScheduleProcessingOfExpiredRequests(mgm)
    }

    private fun loadMgms() {
        mgms.addAll(
            virtualNodeInfoReadService.getAll().mapNotNull {
                val holdingIdentity = it.holdingIdentity
                membershipGroupReaderProvider.getGroupReader(holdingIdentity).lookup(holdingIdentity.x500Name)
                    ?.let { member ->
                        if (member.isMgm) holdingIdentity else null
                    }
            }
        )
    }

    /**
     * Private interface used for implementation swapping in response to lifecycle events.
     */
    private interface InnerExpirationProcessor {
        // scheduled task to query for stuck requests and move them to declined state
        fun cancelOrScheduleProcessingOfExpiredRequests(mgm: HoldingIdentity): Boolean
    }

    @VisibleForTesting
    internal data class DeclineExpiredRegistrationRequests(
        val mgm: HoldingIdentity,
        override val key: String,
        val expirationDate: Long,
    ) : TimerEvent

    private inner class ActiveImpl(membershipConfiguration: SmartConfig) : InnerExpirationProcessor {
        private val expirationDate = membershipConfiguration
            .getLong(MAX_DURATION_BETWEEN_EXPIRED_REGISTRATION_REQUESTS_POLLS).let {
                TimeUnit.MINUTES.toMillis(it)
            }
        private val timeframe = membershipConfiguration
            .getLong(EXPIRATION_DATE_FOR_REGISTRATION_REQUESTS).let {
                TimeUnit.MINUTES.toMillis(it)
            }
        private val maxNoise = (0.1 * timeframe).toInt()

        override fun cancelOrScheduleProcessingOfExpiredRequests(mgm: HoldingIdentity): Boolean {
            mgms.add(mgm)
            coordinator.setTimer(
                key = "DeclineExpiredRegistrationRequests-${mgm.shortHash}",
                // Add noise to prevent all the MGMs to ask for clean-up at the same time (in case of service re-start)
                delay = timeframe - Random.nextInt(maxNoise)
            ) {
                DeclineExpiredRegistrationRequests(mgm, it, expirationDate)
            }
            return true
        }
    }

    private inner class InactiveImpl : InnerExpirationProcessor {
        override fun cancelOrScheduleProcessingOfExpiredRequests(mgm: HoldingIdentity): Boolean {
            logger.warn("$SERVICE is currently inactive.")
            return false
        }
    }

    private fun activate(membershipConfiguration: SmartConfig) {
        impl = ActiveImpl(membershipConfiguration)
        loadMgms()
        mgms.forEach {
            impl.cancelOrScheduleProcessingOfExpiredRequests(it)
        }
    }

    private fun deactivate() {
        impl = InactiveImpl()
        mgms.forEach {
            coordinator.cancelTimer("DeclineExpiredRegistrationRequests-${it.shortHash}")
        }
    }

    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> handleStartEvent(coordinator)
            is StopEvent -> handleStopEvent(coordinator)
            is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
            is ConfigChangedEvent -> handleConfigChangeEvent(event)
            is DeclineExpiredRegistrationRequests -> handleDeclineExpiredRequestsEvent(event.mgm, event.expirationDate)
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        coordinator.createManagedResource(FOLLOW_CHANGES_RESOURCE_NAME) {
            coordinator.followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                    LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
                    LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
                    LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
                )
            )
        }
    }

    private fun handleStopEvent(coordinator: LifecycleCoordinator) {
        deactivate()
        coordinator.closeManagedResources(
            setOf(
                FOLLOW_CHANGES_RESOURCE_NAME,
                WAIT_FOR_CONFIG_RESOURCE_NAME,
                PUBLISHER_RESOURCE_NAME,
            )
        )
        coordinator.updateStatus(LifecycleStatus.DOWN)
    }

    private fun handleRegistrationChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator,
    ) {
        if (event.status == LifecycleStatus.UP) {
            coordinator.createManagedResource(WAIT_FOR_CONFIG_RESOURCE_NAME) {
                configurationReadService.registerComponentForUpdates(
                    coordinator,
                    setOf(BOOT_CONFIG, MESSAGING_CONFIG, MEMBERSHIP_CONFIG)
                )
            }
        } else {
            coordinator.closeManagedResources(setOf(WAIT_FOR_CONFIG_RESOURCE_NAME, PUBLISHER_RESOURCE_NAME))
            deactivate()
            coordinator.updateStatus(LifecycleStatus.DOWN)
        }
    }

    // re-creates the publisher with the new config, sets the lifecycle status to UP when the publisher is ready for the first time
    private fun handleConfigChangeEvent(event: ConfigChangedEvent) {
        val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
        coordinator.createManagedResource(PUBLISHER_RESOURCE_NAME) {
            publisherFactory.createPublisher(
                messagingConfig = messagingConfig,
                publisherConfig = PublisherConfig(
                    PUBLISHER_CLIENT_ID
                )
            ).also {
                it.start()
            }
        }
        activate(event.config.getConfig(MEMBERSHIP_CONFIG))
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun handleDeclineExpiredRequestsEvent(mgm: HoldingIdentity, expirationDate: Long) {
        try {
            if(!impl.cancelOrScheduleProcessingOfExpiredRequests(mgm)) return
            logger.info("Process expired registration requests submitted for membership group '${mgm.groupId}' " +
                    "managed by MGM '${mgm.x500Name}'.")
            val requests = membershipQueryClient.queryRegistrationRequests(
                viewOwningIdentity = mgm,
                statuses = listOf(PENDING_MEMBER_VERIFICATION)
            ).getOrThrow()
            val now = clock.instant()
            val records = requests.mapNotNull {
                if(now.minusMillis(it.registrationLastModified.toEpochMilli()) > Instant.ofEpochMilli(expirationDate)) {
                    logger.info("Registration request with ID '${it.registrationId}' expired. Declining request.")
                    val id = virtualNodeInfoReadService
                        .getByHoldingIdentityShortHash(ShortHash.of(it.holdingIdentityId))
                        ?.holdingIdentity
                        ?: throw IllegalArgumentException("Cannot find information for " +
                                "holding identity with ID '${it.holdingIdentityId}'.")
                    Record(
                        topic = REGISTRATION_COMMAND_TOPIC,
                        key = "${id.x500Name}-${id.groupId}",
                        value = RegistrationCommand(
                            DeclineRegistration(
                                "Registration request stuck and expired.",
                                DECLINED_REASON_FOR_USER_INTERNAL_ERROR
                            )
                        )
                    )
                } else {
                    null
                }
            }
            publishRecords(records)
            logger.info("Published decline registration command for expired registration requests for " +
                    "membership group '${mgm.groupId}' managed by MGM '${mgm.x500Name}'.")
        } catch (e: Exception) {
            logger.warn("Could not process expired registration requests for membership group '${mgm.groupId}' " +
                    "managed by MGM '${mgm.x500Name}'.", e)
        }
    }

    private fun publishRecords(records: List<Record<String, RegistrationCommand>>) {
        coordinator.getManagedResource<Publisher>(PUBLISHER_RESOURCE_NAME)
            ?.publish(records)
            ?.forEach { it.join() }
    }
}