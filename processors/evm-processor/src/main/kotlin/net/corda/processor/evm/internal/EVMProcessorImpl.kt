package net.corda.processor.evm.internal

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.interop.evm.dispatcher.factory.GenericDispatcherFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.processor.evm.EVMProcessor
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys.INTEROP_EVM_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import okhttp3.OkHttpClient
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory


@Component(service = [EVMProcessor::class])
@Suppress("Unused", "LongParameterList")
class EVMProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
) : EVMProcessor {

    private val configKeys = setOf(INTEROP_EVM_CONFIG)

    private val httpClient = OkHttpClient()

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
    )

    private val lifecycleCoordinator =
        coordinatorFactory.createCoordinator<EVMProcessorImpl>(dependentComponents, ::eventHandler)

    @Volatile
    private var dependenciesUp: Boolean = false


    override fun start(bootConfig: SmartConfig) {
        log.info("EVM processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() {
        log.info("EVM processor stopping.")
        lifecycleCoordinator.stop()
    }


    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                log.trace("EVM Processor starting")
            }

            is StopEvent -> {
                log.trace("Stopping EVM Processor")
            }

            is BootConfigEvent -> {
                val bootstrapConfig = event.config
                log.trace("Bootstrapping {}", configurationReadService::class.simpleName)
                configurationReadService.bootstrapConfig(bootstrapConfig)
            }

            is RegistrationStatusChangeEvent -> {
                log.trace("Registering for configuration updates.")
                configurationReadService.registerComponentForUpdates(coordinator, configKeys)
            }

            is ConfigChangedEvent -> {
                log.trace("Config Changed Event")
                coordinator.updateStatus(LifecycleStatus.UP)

                val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
                val evmConfig = event.config.getConfig(INTEROP_EVM_CONFIG)

                coordinator.createManagedResource("EVM_OPS_PROCESSOR") {
                    subscriptionFactory.createRPCSubscription(
                        rpcConfig = RPCConfig(
                            groupName = "evm.ops.rpc",
                            clientName = "evm.ops.rpc",
                            requestTopic = Schemas.Interop.EVM_REQUEST,
                            requestType = EvmRequest::class.java,
                            responseType = EvmResponse::class.java
                        ),
                        responderProcessor = EVMOpsProcessor(GenericDispatcherFactory, httpClient, evmConfig),
                        messagingConfig = messagingConfig
                    )
                }

            }
        }
    }

    data class BootConfigEvent(val config: SmartConfig) : LifecycleEvent
}