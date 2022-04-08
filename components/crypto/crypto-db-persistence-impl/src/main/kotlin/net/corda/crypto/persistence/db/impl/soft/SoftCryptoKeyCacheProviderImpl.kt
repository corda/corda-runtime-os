package net.corda.crypto.persistence.db.impl.soft

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.persistence.SoftCryptoKeyCache
import net.corda.crypto.persistence.SoftCryptoKeyCacheProvider
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.persistence.config.softPersistence
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManagerFactory

@Component(service = [SoftCryptoKeyCacheProvider::class])
class SoftCryptoKeyCacheProviderImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata
) : AbstractConfigurableComponent<SoftCryptoKeyCacheProviderImpl.Impl>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<SoftCryptoKeyCacheProvider>(),
    configurationReadService,
    InactiveImpl(),
    setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
    ),
    setOf(
        ConfigKeys.MESSAGING_CONFIG,
        ConfigKeys.BOOT_CONFIG,
        ConfigKeys.CRYPTO_CONFIG
    )
), SoftCryptoKeyCacheProvider {
    interface Impl: AutoCloseable {
        fun getInstance(passphrase: String, salt: String): SoftCryptoKeyCache
    }

    override fun createInactiveImpl(): Impl = InactiveImpl()

    override fun createActiveImpl(event: ConfigChangedEvent): Impl =
        ActiveImpl(event, dbConnectionManager, schemeMetadata)

    override fun getInstance(passphrase: String, salt: String): SoftCryptoKeyCache =
        impl.getInstance(passphrase, salt)

    class InactiveImpl : Impl {
        override fun getInstance(passphrase: String, salt: String): SoftCryptoKeyCache =
            throw IllegalStateException("The component is in illegal state.")

        override fun close() = Unit
    }

    class ActiveImpl(
        event: ConfigChangedEvent,
        dbConnectionManager: DbConnectionManager,
        private val schemeMetadata: CipherSchemeMetadata
    ) : Impl {
        private val config: SmartConfig

        private val entityManagerFactory: EntityManagerFactory = dbConnectionManager.getClusterEntityManagerFactory()

        init {
            config = event.config[ConfigKeys.CRYPTO_CONFIG] ?:
                    SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())
        }

        private val cache: Cache<String, WrappingKey> = Caffeine.newBuilder()
            .expireAfterAccess(config.softPersistence.expireAfterAccessMins, TimeUnit.MINUTES)
            .maximumSize(config.softPersistence.maximumSize)
            .build()

        override fun getInstance(passphrase: String, salt: String): SoftCryptoKeyCache =
            SoftCryptoKeyCacheImpl(
                entityManagerFactory = entityManagerFactory,
                cache = cache,
                masterKey = WrappingKey.derive(
                    schemeMetadata = schemeMetadata,
                    passphrase = passphrase,
                    salt = salt
                )
            )

        override fun close() {
            cache.invalidateAll()
            entityManagerFactory.close()
        }
    }
}