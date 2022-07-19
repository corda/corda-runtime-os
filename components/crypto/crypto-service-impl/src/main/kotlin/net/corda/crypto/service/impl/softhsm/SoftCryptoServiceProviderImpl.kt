package net.corda.crypto.service.impl.softhsm

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_SERVICE_NAME
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.impl.config.CryptoSoftHSMConfig
import net.corda.crypto.impl.config.KEY_MAP_CACHING_NAME
import net.corda.crypto.impl.config.KEY_MAP_TRANSIENT_NAME
import net.corda.crypto.impl.config.WRAPPING_DEFAULT_NAME
import net.corda.crypto.impl.config.WRAPPING_HSM_NAME
import net.corda.crypto.impl.config.softHSM
import net.corda.crypto.impl.config.toCryptoConfig
import net.corda.crypto.persistence.wrapping.WrappingKeyStore
import net.corda.crypto.service.softhsm.SoftCryptoServiceConfig
import net.corda.crypto.service.softhsm.SoftCryptoServiceProvider
import net.corda.crypto.service.softhsm.SoftKeyMap
import net.corda.crypto.service.softhsm.SoftPrivateKeyWrapping
import net.corda.crypto.service.softhsm.SoftWrappingKeyMap
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceProvider
import net.corda.v5.crypto.DigestService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component(service = [CryptoServiceProvider::class, SoftCryptoServiceProvider::class])
open class SoftCryptoServiceProviderImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = DigestService::class)
    private val digestService: DigestService,
    @Reference(service = WrappingKeyStore::class)
    private val store: WrappingKeyStore
) : AbstractConfigurableComponent<SoftCryptoServiceProviderImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = lifecycleCoordinatorName,
    configurationReadService = configurationReadService,
    upstream = DependenciesTracker.Default(
        setOf(LifecycleCoordinatorName.forComponent<WrappingKeyStore>())
    ),
    configKeys = setOf(
        CRYPTO_CONFIG
    )
), SoftCryptoServiceProvider {
    companion object {
        private val logger: Logger = contextLogger()
        private val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<SoftCryptoServiceProvider>()
    }

    override fun createActiveImpl(event: ConfigChangedEvent): Impl = Impl(event, schemeMetadata, digestService, store)

    override val name: String = SOFT_HSM_SERVICE_NAME

    override val configType: Class<SoftCryptoServiceConfig> = SoftCryptoServiceConfig::class.java

    override fun getInstance(config: SoftCryptoServiceConfig): CryptoService = impl.getInstance()

    override val lifecycleName: LifecycleCoordinatorName get() = lifecycleCoordinatorName

    class Impl(
        event: ConfigChangedEvent,
        private val schemeMetadata: CipherSchemeMetadata,
        private val digestService: DigestService,
        private val store: WrappingKeyStore
    ) : DownstreamAlwaysUpAbstractImpl() {
        private val config: CryptoSoftHSMConfig = event.config.toCryptoConfig().softHSM()

        fun getInstance(): CryptoService {
            logger.info("Creating instance of the {}", SoftCryptoService::class.java.name)
            val wrappingKeyMap = createSoftWrappingKeyMap()
            val privateKeyWrapping = createSoftPrivateKeyWrapping(wrappingKeyMap)
            val keyMap = createSoftKeyMap(privateKeyWrapping)
            return SoftCryptoService(
                keyMap = keyMap,
                wrappingKeyMap = wrappingKeyMap,
                schemeMetadata = schemeMetadata,
                digestService = digestService
            )
        }

        private fun createSoftWrappingKeyMap(): SoftWrappingKeyMap {
            val master = WrappingKey.derive(
                schemeMetadata = schemeMetadata,
                passphrase = config.passphrase,
                salt = config.salt
            )
            return when (config.wrappingKeyMap.name) {
                KEY_MAP_TRANSIENT_NAME -> TransientSoftWrappingKeyMap(store, master)
                KEY_MAP_CACHING_NAME -> CachingSoftWrappingKeyMap(config.wrappingKeyMap.cache, store, master)
                else -> throw IllegalStateException(
                    "Unknown configuration value '${config.wrappingKeyMap.name}' for " +
                            "${SoftWrappingKeyMap::class.java.simpleName}, must be " +
                            "$KEY_MAP_TRANSIENT_NAME or $KEY_MAP_CACHING_NAME."
                )
            }
        }

        private fun createSoftPrivateKeyWrapping(wrappingKeyMap: SoftWrappingKeyMap): SoftPrivateKeyWrapping =
            when (config.wrapping.name) {
                WRAPPING_DEFAULT_NAME -> DefaultSoftPrivateKeyWrapping(wrappingKeyMap)
                WRAPPING_HSM_NAME -> throw NotImplementedError(
                    "${SoftPrivateKeyWrapping::class.java.simpleName} is not implemented."
                )
                else -> throw IllegalStateException(
                    "Unknown configuration value '${config.wrapping.name}' for " +
                            "${SoftPrivateKeyWrapping::class.java.simpleName}, must be " +
                            "$WRAPPING_DEFAULT_NAME or $WRAPPING_HSM_NAME"
                )
            }

        private fun createSoftKeyMap(wrapping: SoftPrivateKeyWrapping): SoftKeyMap =
            when (config.keyMap.name) {
                KEY_MAP_TRANSIENT_NAME -> TransientSoftKeyMap(wrapping)
                KEY_MAP_CACHING_NAME -> CachingSoftKeyMap(config.keyMap.cache, wrapping)
                else -> throw IllegalStateException(
                    "Unknown configuration value '${config.keyMap.name}' for " +
                            "${SoftKeyMap::class.java.simpleName}, must be " +
                            "$KEY_MAP_TRANSIENT_NAME or $KEY_MAP_CACHING_NAME."
                )
            }
    }
}