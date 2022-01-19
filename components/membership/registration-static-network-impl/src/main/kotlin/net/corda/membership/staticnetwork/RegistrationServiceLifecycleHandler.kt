package net.corda.membership.staticnetwork

import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.CryptoLibraryClientsFactoryProvider
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.schema.configuration.ConfigKeys.Companion.MESSAGING_CONFIG
import net.corda.v5.cipher.suite.KeyEncodingService

interface RegistrationServiceLifecycleHandler : LifecycleEventHandler {
    class Impl(
        private val staticMemberRegistrationService: StaticMemberRegistrationService
    ) : RegistrationServiceLifecycleHandler {
        // for watching the config changes
        private var configRegistrationHandle: AutoCloseable? = null
        // for checking the components' health
        private var componentRegistrationHandle: AutoCloseable? = null

        // TODO will need to add start() and close()
        private val groupPolicyProvider = staticMemberRegistrationService.groupPolicyProvider

        // no lifecycle
        private val publisherFactory = staticMemberRegistrationService.publisherFactory

        // no lifecycle
        private val cryptoLibraryFactory = staticMemberRegistrationService.cryptoLibraryFactory

        private val configurationReadService = staticMemberRegistrationService.configurationReadService

        private lateinit var keyEncodingService: KeyEncodingService

        private lateinit var publisher: Publisher

        override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            when(event) {
                is StartEvent -> handleStartEvent(coordinator)
                is StopEvent -> handleStopEvent()
                is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
                is MessagingConfigurationReceived -> handleConfigChange(event)
            }
            TODO("Not yet implemented")
        }

        private fun handleStartEvent(coordinator: LifecycleCoordinator) {
            configurationReadService.start()
            keyEncodingService = cryptoLibraryFactory.getKeyEncodingService()
            componentRegistrationHandle = coordinator.followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
                    LifecycleCoordinatorName.forComponent<CryptoLibraryClientsFactoryProvider>(),
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                )
            )
        }

        private fun handleStopEvent() {
            configurationReadService.stop()
            componentRegistrationHandle?.close()
            configRegistrationHandle?.close()
            publisher.close()
        }

        private fun handleRegistrationChangeEvent(
            event: RegistrationStatusChangeEvent,
            coordinator: LifecycleCoordinator
        ) {
            // Respond to config read service lifecycle status change
            when (event.status) {
                LifecycleStatus.UP -> {
                    configRegistrationHandle = configurationReadService.registerForUpdates(
                        MessagingConfigurationHandler(coordinator)
                    )
                    coordinator.updateStatus(LifecycleStatus.UP)
                }
                else -> {
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                    configRegistrationHandle?.close()
                }
            }
        }

        // re-creates the publisher with the new config
        private fun handleConfigChange(event: MessagingConfigurationReceived) {
            publisher = publisherFactory.createPublisher(
                PublisherConfig("static-member-registration-service"),
                event.config
            )
            publisher.start()
        }

        class MessagingConfigurationHandler(val coordinator: LifecycleCoordinator) : ConfigurationHandler {
            override fun onNewConfiguration(keys: Set<String>, config: Map<String, SmartConfig>) {
                if(MESSAGING_CONFIG in keys) {
                    coordinator.postEvent(MessagingConfigurationReceived(config[MESSAGING_CONFIG]!!))
                }
            }
        }
    }

}

data class MessagingConfigurationReceived(val config: SmartConfig) : LifecycleEvent
/*
    keyEncodingService = cryptoLibraryFactory.getKeyEncodingService()
        // temporary solution until we don't have a more suitable category
        signingService = cryptoLibraryClientsFactory.getSigningService(CryptoCategories.LEDGER)
        publisher = publisherFactory.createPublisher(PublisherConfig("static-member-registration-service"))
     */