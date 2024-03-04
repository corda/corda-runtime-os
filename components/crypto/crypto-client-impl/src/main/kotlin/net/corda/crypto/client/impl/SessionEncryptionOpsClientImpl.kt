package net.corda.crypto.client.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.SessionEncryptionOpsClient
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [SessionEncryptionOpsClient::class])
class SessionEncryptionOpsClientImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = PlatformInfoProvider::class)
    val platformInfoProvider: PlatformInfoProvider,
) : AbstractConfigurableComponent<SessionEncryptionOpsClientImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<SessionEncryptionOpsClient>(),
    configurationReadService = configurationReadService,
    configKeys = setOf(BOOT_CONFIG),
),
    SessionEncryptionOpsClient {
    override fun encryptSessionData(plainBytes: ByteArray, alias: String?): ByteArray =
        impl.ops.encryptSessionData(plainBytes, alias)

    override fun decryptSessionData(cipherBytes: ByteArray, alias: String?): ByteArray =
        impl.ops.decryptSessionData(cipherBytes, alias)

    override fun createActiveImpl(event: ConfigChangedEvent): Impl =
        Impl(publisherFactory, platformInfoProvider, event)

    class Impl(
        publisherFactory: PublisherFactory,
        platformInfoProvider: PlatformInfoProvider,
        event: ConfigChangedEvent,
    ) : AbstractImpl {
        val ops = SessionEncryptionImpl(
            publisherFactory.createHttpRpcClient(),
            platformInfoProvider,
            event.config.getConfig(BOOT_CONFIG),
        )
        override val downstream = DependenciesTracker.AlwaysUp()
    }
}
