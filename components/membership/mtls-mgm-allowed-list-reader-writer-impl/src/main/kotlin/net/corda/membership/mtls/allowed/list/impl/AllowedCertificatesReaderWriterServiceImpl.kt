package net.corda.membership.mtls.allowed.list.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.p2p.mtls.MgmAllowedCertificateSubject
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.mtls.allowed.list.service.AllowedCertificatesReaderWriterService
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.reconciliation.VersionedRecord
import net.corda.schema.Schemas.P2P.P2P_MGM_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS
import net.corda.schema.configuration.ConfigKeys
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream

@Component(service = [AllowedCertificatesReaderWriterService::class])
class AllowedCertificatesReaderWriterServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
): AllowedCertificatesReaderWriterService {
    private companion object {
        const val FOLLOW_CHANGES_RESOURCE_NAME = "AllowedCertificatesReaderWriterService.followStatusChangesByName"
        const val WAIT_FOR_CONFIG_RESOURCE_NAME = "AllowedCertificatesReaderWriterService.registerComponentForUpdates"
        const val SUBSCRIPTION_RESOURCE_NAME = "AllowedCertificatesReaderWriterService.subscription"
        const val PUBLISHER_RESOURCE_NAME = "AllowedCertificatesReaderWriterService.publisher"
        const val SUBSCRIPTION_GROUP_NAME = "mgm-allowed-certificate-subjects-reader"
        const val PUBLISHER_CLIENT_ID = "mgm-allowed-certificate-subjects-writer"
    }
    private val publishedSubjects = ConcurrentHashMap.newKeySet<MgmAllowedCertificateSubject>()
    private val coordinator = coordinatorFactory.createCoordinator<AllowedCertificatesReaderWriterService> {event, _ ->
        handleEvent(event)
    }
    private fun handleEvent(event: LifecycleEvent) {
        when(event) {
            is StartEvent -> {
                coordinator.createManagedResource(FOLLOW_CHANGES_RESOURCE_NAME) {
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                        )
                    )
                }
            }
            is StopEvent -> {
                coordinator.closeManagedResources(
                    setOf(
                        FOLLOW_CHANGES_RESOURCE_NAME,
                        WAIT_FOR_CONFIG_RESOURCE_NAME,
                        SUBSCRIPTION_RESOURCE_NAME,
                        PUBLISHER_RESOURCE_NAME,
                    )
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
                            )
                        )
                    }
                } else {
                    coordinator.closeManagedResources(
                        setOf(
                            WAIT_FOR_CONFIG_RESOURCE_NAME
                        )
                    )
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }
            is ConfigChangedEvent -> {
                val messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG)
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
                coordinator.createManagedResource(SUBSCRIPTION_RESOURCE_NAME) {
                    subscriptionFactory.createCompactedSubscription(
                        subscriptionConfig = SubscriptionConfig(
                            groupName = SUBSCRIPTION_GROUP_NAME,
                            P2P_MGM_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS,
                        ),
                        processor = Processor(),
                        messagingConfig = messagingConfig,
                    ).also {
                        it.start()
                    }
                }
            }
        }
    }

    private inner class Processor: CompactedProcessor<String, MgmAllowedCertificateSubject> {
        override val keyClass = String::class.java
        override val valueClass = MgmAllowedCertificateSubject::class.java

        override fun onNext(
            newRecord: Record<String, MgmAllowedCertificateSubject>,
            oldValue: MgmAllowedCertificateSubject?,
            currentData: Map<String, MgmAllowedCertificateSubject>,
        ) {
            val newData = newRecord.value
            if(newData != null) {
                publishedSubjects.add(newData)
            } else {
                if (oldValue != null) {
                    publishedSubjects.remove(oldValue)
                }
            }
        }

        override fun onSnapshot(currentData: Map<String, MgmAllowedCertificateSubject>) {
            publishedSubjects.addAll(currentData.values)
            coordinator.updateStatus(LifecycleStatus.UP)
        }

    }

    override fun getAllVersionedRecords():
            Stream<VersionedRecord<MgmAllowedCertificateSubject, MgmAllowedCertificateSubject>>? {
        return publishedSubjects.stream().map {
            object: VersionedRecord<MgmAllowedCertificateSubject, MgmAllowedCertificateSubject> {
                override val version = 1
                override val isDeleted = false
                override val key = it
                override val value = it
            }
        }
    }

    private fun MgmAllowedCertificateSubject.key(): String
        = "${this.groupId};${this.subject}"

    override fun put(recordKey: MgmAllowedCertificateSubject, recordValue: MgmAllowedCertificateSubject) {
        coordinator.getManagedResource<Publisher>(PUBLISHER_RESOURCE_NAME)?.publish(
            listOf(
                Record(
                    topic = P2P_MGM_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS,
                    key = recordKey.key(),
                    value = recordValue,
                )
            )
        )?.forEach {
            it.join()
        }
    }

    override fun remove(recordKey: MgmAllowedCertificateSubject) {
        coordinator.getManagedResource<Publisher>(PUBLISHER_RESOURCE_NAME)?.publish(
            listOf(
                Record(
                    topic = P2P_MGM_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS,
                    key = recordKey.key(),
                    value = null,
                )
            )
        )?.forEach {
            it.join()
        }
    }

    override val isRunning: Boolean
        get() = coordinator.status == LifecycleStatus.UP

    override val lifecycleCoordinatorName: LifecycleCoordinatorName = coordinator.name

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}