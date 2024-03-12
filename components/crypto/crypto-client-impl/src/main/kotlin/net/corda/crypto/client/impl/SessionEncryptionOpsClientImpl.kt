package net.corda.crypto.client.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.SessionEncryptionOpsClient
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.utilities.trace
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [SessionEncryptionOpsClient::class])
class SessionEncryptionOpsClientImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = PlatformInfoProvider::class)
    val platformInfoProvider: PlatformInfoProvider,
) : SessionEncryptionOpsClient {

    companion object {
        private val logger = LoggerFactory.getLogger(SessionEncryptionOpsClientImpl::class.java)
    }

    private val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<SessionEncryptionOpsClient>()
    // VisibleForTesting
    val lifecycleCoordinator = coordinatorFactory.createCoordinator(
        lifecycleCoordinatorName,
        ::eventHandler
    )
    private val myName = lifecycleCoordinatorName

    private var _impl: Impl? = null
    val impl: Impl get() {
        val tmp = _impl
        if(tmp == null || lifecycleCoordinator.status != LifecycleStatus.UP) {
            throw IllegalStateException("Component $myName is not ready.")
        }
        return tmp
    }

    override fun encryptSessionData(plainBytes: ByteArray, alias: String?): ByteArray =
        impl.ops.encryptSessionData(plainBytes, alias)

    override fun decryptSessionData(cipherBytes: ByteArray, alias: String?): ByteArray =
        impl.ops.decryptSessionData(cipherBytes, alias)

    class Impl(
        publisherFactory: PublisherFactory,
        platformInfoProvider: PlatformInfoProvider,
        event: ConfigChangedEvent,
    ) {
        val ops = SessionEncryptionImpl(
            publisherFactory.createHttpRpcClient(),
            platformInfoProvider,
            event.config.getConfig(BOOT_CONFIG),
        )
    }

    private var configReadServiceRegistrationHandle: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.trace { "LifecycleEvent received $myName: $event" }
        when (event) {
            is StartEvent -> {
                configReadServiceRegistrationHandle?.close()
                configReadServiceRegistrationHandle = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                    )
                )
            }
            is StopEvent -> {
                onStop()
            }
            is RegistrationStatusChangeEvent -> {
                if (event.registration == configReadServiceRegistrationHandle) {
                    configHandle?.close()
                    if (event.status == LifecycleStatus.UP) {
                        logger.trace { "Registering for configuration updates." }
                        configHandle =
                            configurationReadService.registerComponentForUpdates(coordinator, setOf(BOOT_CONFIG))
                    } else {
                        coordinator.updateStatus(LifecycleStatus.DOWN)
                    }
                }
            }
            is ConfigChangedEvent -> {
                logger.trace { "Activating $myName" }
                _impl = Impl(publisherFactory, platformInfoProvider, event)
                coordinator.updateStatus(LifecycleStatus.UP)
                logger.trace { "Activated $myName" }
            }
        }
    }

    private fun onStop() {
        configHandle?.close()
        configHandle = null
        configReadServiceRegistrationHandle?.close()
        configReadServiceRegistrationHandle = null
        _impl = null
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        logger.trace { "$myName starting..." }
        lifecycleCoordinator.start()
    }

    override fun stop() {
        logger.trace { "$myName stopping..." }
        lifecycleCoordinator.stop()
    }
}
