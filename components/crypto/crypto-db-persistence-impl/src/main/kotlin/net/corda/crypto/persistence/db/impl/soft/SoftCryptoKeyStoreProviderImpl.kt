package net.corda.crypto.persistence.db.impl.soft

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.impl.config.softPersistence
import net.corda.crypto.impl.config.toCryptoConfig
import net.corda.crypto.persistence.soft.SoftCryptoKeyStore
import net.corda.crypto.persistence.soft.SoftCryptoKeyStoreProvider
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [SoftCryptoKeyStoreProvider::class])
class SoftCryptoKeyStoreProviderImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata
) : AbstractConfigurableComponent<SoftCryptoKeyStoreProviderImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<SoftCryptoKeyStoreProvider>(),
    configurationReadService = configurationReadService,
    upstream = DependenciesTracker.Default(
        setOf(
            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
            LifecycleCoordinatorName.forComponent<DbConnectionManager>()
        )
    ),
    configKeys = setOf(
        ConfigKeys.MESSAGING_CONFIG,
        ConfigKeys.BOOT_CONFIG,
        ConfigKeys.CRYPTO_CONFIG
    )
), SoftCryptoKeyStoreProvider {
    override fun createActiveImpl(event: ConfigChangedEvent): Impl =
        Impl(coordinatorFactory, event, dbConnectionManager, schemeMetadata)

    override fun getInstance(): SoftCryptoKeyStore =
        impl.getInstance()

    class Impl(
        coordinatorFactory: LifecycleCoordinatorFactory,
        event: ConfigChangedEvent,
        private val dbConnectionOps: DbConnectionOps,
        private val schemeMetadata: CipherSchemeMetadata
    ) : AbstractImpl {
        private val config: SmartConfig

        init {
            config = event.config.toCryptoConfig()
        }

        private val _instance: SoftCryptoKeyStore by lazy {
            val softConfig = config.softPersistence()
            SoftCryptoKeyStoreImpl(
                config = softConfig,
                entityManagerFactory = dbConnectionOps.getOrCreateEntityManagerFactory(
                    CordaDb.Crypto,
                    DbPrivilege.DML
                ),
                masterKey = WrappingKey.derive(
                    schemeMetadata = schemeMetadata,
                    passphrase = softConfig.passphrase,
                    salt = softConfig.salt
                )
            )
        }

        fun getInstance(): SoftCryptoKeyStore = _instance

        private val _downstream = DependenciesTracker.AlwaysUp(
            coordinatorFactory,
            this
        ).also { it.start() }

        override val downstream: DependenciesTracker = _downstream

        override fun close() {
            _downstream.close()
            _instance.close()
        }
    }
}