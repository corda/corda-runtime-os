package net.corda.membership.locally.hosted.identities.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.locally.hosted.identities.IdentityInfo
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.reconciliation.VersionedRecord
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.crypto.publicKeyFactory
import net.corda.utilities.millis
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.io.Reader
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream

@Component(service = [LocallyHostedIdentitiesService::class])
@Suppress("LongParameterList")
class LocallyHostedIdentitiesServiceImpl(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val subscriptionFactory: SubscriptionFactory,
    private val configurationReadService: ConfigurationReadService,
    private val certificateFactory: CertificateFactory,
    private val publicKeyFactory: (Reader) -> PublicKey?,
    private val sleeper: ((Long) -> Unit),
) : LocallyHostedIdentitiesService {
    @Activate constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = SubscriptionFactory::class)
        subscriptionFactory: SubscriptionFactory,
        @Reference(service = ConfigurationReadService::class)
        configurationReadService: ConfigurationReadService,
    ) : this(
        coordinatorFactory,
        subscriptionFactory,
        configurationReadService,
        CertificateFactory.getInstance("X.509"),
        ::publicKeyFactory,
        {
            Thread.sleep(it)
        },
    )
    private companion object {
        const val FOLLOW_CHANGES_RESOURCE_NAME = "LocallyHostedIdentitiesServiceImpl.followStatusChangesByName"
        const val WAIT_FOR_CONFIG_RESOURCE_NAME = "LocallyHostedIdentitiesServiceImpl.registerComponentForUpdates"
        const val SUBSCRIPTION_RESOURCE_NAME = "LocallyHostedIdentitiesServiceImpl.subscription"
        const val SUBSCRIPTION_GROUP_NAME = "locally-hosted-identities-service"
        const val defaultRetries = 4
        val waitBetweenRetries = 100.millis
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val identities = ConcurrentHashMap<HoldingIdentity, HostedIdentityEntry>()
    private val coordinator = coordinatorFactory.createCoordinator<LocallyHostedIdentitiesService> { event, _ ->
        handleEvent(event)
    }

    private fun handleEvent(event: LifecycleEvent) {
        when (event) {
            is StartEvent -> {
                coordinator.createManagedResource(FOLLOW_CHANGES_RESOURCE_NAME) {
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                        ),
                    )
                }
            }
            is StopEvent -> {
                coordinator.closeManagedResources(
                    setOf(
                        FOLLOW_CHANGES_RESOURCE_NAME,
                        WAIT_FOR_CONFIG_RESOURCE_NAME,
                        SUBSCRIPTION_RESOURCE_NAME,
                    ),
                )
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    coordinator.createManagedResource(WAIT_FOR_CONFIG_RESOURCE_NAME) {
                        configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(
                                ConfigKeys.BOOT_CONFIG,
                                ConfigKeys.MESSAGING_CONFIG,
                            ),
                        )
                    }
                } else {
                    coordinator.closeManagedResources(
                        setOf(
                            WAIT_FOR_CONFIG_RESOURCE_NAME,
                        ),
                    )
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }
            is ConfigChangedEvent -> {
                coordinator.createManagedResource(SUBSCRIPTION_RESOURCE_NAME) {
                    subscriptionFactory.createCompactedSubscription(
                        subscriptionConfig = SubscriptionConfig(
                            groupName = SUBSCRIPTION_GROUP_NAME,
                            Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC,
                        ),
                        processor = Processor(),
                        messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG),
                    ).also {
                        it.start()
                    }
                }
            }
        }
    }

    private inner class Processor : CompactedProcessor<String, HostedIdentityEntry> {
        override val keyClass = String::class.java
        override val valueClass = HostedIdentityEntry::class.java

        override fun onNext(
            newRecord: Record<String, HostedIdentityEntry>,
            oldValue: HostedIdentityEntry?,
            currentData: Map<String, HostedIdentityEntry>,
        ) {
            val newEntry = newRecord.value
            if (newEntry == null) {
                if (oldValue != null) {
                    identities.remove(
                        oldValue.holdingIdentity.toCorda(),
                    )
                }
            } else {
                identities[newEntry.holdingIdentity.toCorda()] = newEntry
            }
        }

        override fun onSnapshot(currentData: Map<String, HostedIdentityEntry>) {
            currentData.values.forEach {
                identities[it.holdingIdentity.toCorda()] = it
            }
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    private fun HostedIdentityEntry.toIdentityInfo(): IdentityInfo? {
        val preferredSessionKeyPem = this.preferredSessionKeyAndCert.sessionPublicKey
        val preferredSessionKey = publicKeyFactory(
            preferredSessionKeyPem.reader(),
        )
        return if (preferredSessionKey == null) {
            logger.warn(
                "Hosted Identity entry for ${this.holdingIdentity.toCorda()} had invalid " +
                    "preferred session key (preferredSessionKeyPem). Ignoring this record.",
            )
            null
        } else {
            IdentityInfo(
                identity = this.holdingIdentity.toCorda(),
                tlsCertificates = this.tlsCertificates.toCertificates(),
                preferredSessionKey = preferredSessionKey,
            )
        }
    }

    private fun Collection<String>.toCertificates(): List<X509Certificate> {
        return this.flatMap { pem ->
            pem.byteInputStream().use { input ->
                certificateFactory.generateCertificates(input)
            }
        }.filterIsInstance<X509Certificate>()
    }

    private fun pollForIdentityInfo(
        identity: HoldingIdentity,
        retries: Int,
    ): IdentityInfo? {
        if (!isRunning) {
            throw CordaRuntimeException("Service is not ready")
        }
        val known = identities[identity]?.toIdentityInfo()
        if (known != null) {
            return known
        }
        if (retries <= 0) {
            logger.warn("Identity {} is unknown!", identity)
            return null
        }
        logger.info("Identity {} is unknown yet, will retry in a while", identity)
        sleeper(waitBetweenRetries.toMillis())
        return pollForIdentityInfo(identity, retries - 1)
    }

    override fun pollForIdentityInfo(identity: HoldingIdentity): IdentityInfo? =
        pollForIdentityInfo(identity, defaultRetries)

    override fun getAllVersionedRecords(): Stream<VersionedRecord<String, HostedIdentityEntry>> =
        identities.values.stream()
            .map {
                object : VersionedRecord<String, HostedIdentityEntry> {
                    override val version = it.version ?: 1
                    override val isDeleted = false
                    override val key = it.holdingIdentity.toCorda().shortHash.value
                    override val value = it
                }
            }

    override val lifecycleCoordinatorName: LifecycleCoordinatorName
        get() = coordinator.name

    override fun isHostedLocally(identity: HoldingIdentity): Boolean {
        if (!isRunning) {
            throw CordaRuntimeException("Service is not ready")
        }
        return identities.containsKey(identity)
    }

    override val isRunning
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}
