package net.corda.crypto.softhsm.impl

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.ConfigurationSecrets
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.CryptoServiceProvider
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.component.impl.AbstractComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_SERVICE_NAME
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.persistence.WrappingKeyStore
import net.corda.crypto.softhsm.KEY_MAP_CACHING_NAME
import net.corda.crypto.softhsm.KEY_MAP_TRANSIENT_NAME
import net.corda.crypto.softhsm.SoftCryptoServiceConfig
import net.corda.crypto.softhsm.SoftCryptoServiceProvider
import net.corda.crypto.softhsm.SoftKeyMap
import net.corda.crypto.softhsm.SoftPrivateKeyWrapping
import net.corda.crypto.softhsm.SoftWrappingKeyMap
import net.corda.crypto.softhsm.SoftWrappingKeyMapConfig
import net.corda.crypto.softhsm.WRAPPING_DEFAULT_NAME
import net.corda.crypto.softhsm.WRAPPING_HSM_NAME
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * The service ranking is set to the smallest possible number to allow the upstream implementation to pick other
 * providers as this one will always be present in the deployment.
 */
@ServiceRanking(Int.MIN_VALUE)
@Component(service = [CryptoServiceProvider::class, SoftCryptoServiceProvider::class])
open class SoftCryptoServiceProviderImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = PlatformDigestService::class)
    private val digestService: PlatformDigestService,
    @Reference(service = WrappingKeyStore::class)
    private val store: WrappingKeyStore
) : AbstractComponent<SoftCryptoServiceProviderImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = lifecycleCoordinatorName,
    upstream = DependenciesTracker.Default(
        setOf(
            LifecycleCoordinatorName.forComponent<WrappingKeyStore>()
        )
    )
), SoftCryptoServiceProvider {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<SoftCryptoServiceProvider>()
    }

    override fun createActiveImpl(): Impl = Impl(schemeMetadata, digestService, store)

    override val name: String = SOFT_HSM_SERVICE_NAME

    override val configType: Class<SoftCryptoServiceConfig> = SoftCryptoServiceConfig::class.java

    override fun getInstance(config: SoftCryptoServiceConfig, secrets: ConfigurationSecrets): CryptoService =
        impl.getInstance(config, secrets)

    override val lifecycleName: LifecycleCoordinatorName get() = lifecycleCoordinatorName

    class Impl(
        private val schemeMetadata: CipherSchemeMetadata,
        private val digestService: PlatformDigestService,
        private val store: WrappingKeyStore
    ) : AbstractImpl {

        fun getInstance(config: SoftCryptoServiceConfig, secrets: ConfigurationSecrets): CryptoService {
            logger.info("Creating instance of the {}", SoftCryptoService::class.java.name)
            val wrappingKeyMap = createSoftWrappingKeyMap(config, secrets)
            val privateKeyWrapping = createSoftPrivateKeyWrapping(config, wrappingKeyMap)
            val keyMap = createSoftKeyMap(config, privateKeyWrapping)
            return SoftCryptoService(
                keyMap = keyMap,
                wrappingKeyMap = wrappingKeyMap,
                schemeMetadata = schemeMetadata,
                digestService = digestService
            )
        }

        private fun createSoftWrappingKeyMap(
            config: SoftCryptoServiceConfig,
            secrets: ConfigurationSecrets
        ): SoftWrappingKeyMap =
            when (config.wrappingKeyMap.name) {
                KEY_MAP_TRANSIENT_NAME -> TransientSoftWrappingKeyMap(
                    store,
                    createMasterWrappingKey(config.wrappingKeyMap, secrets)
                )
                KEY_MAP_CACHING_NAME -> CachingSoftWrappingKeyMap(
                    config.wrappingKeyMap.cache,
                    store,
                    createMasterWrappingKey(config.wrappingKeyMap, secrets)
                )
                else -> throw IllegalStateException(
                    "Unknown configuration value '${config.wrappingKeyMap.name}' for " +
                            "${SoftWrappingKeyMap::class.java.simpleName}, must be " +
                            "$KEY_MAP_TRANSIENT_NAME or $KEY_MAP_CACHING_NAME."
                )
            }

        private fun createMasterWrappingKey(
            config: SoftWrappingKeyMapConfig,
            secrets: ConfigurationSecrets
        ) = WrappingKey.derive(
            schemeMetadata = schemeMetadata,
            salt = config.salt,
            passphrase = secrets.getSecret(config.passphrase),
        )

        private fun createSoftPrivateKeyWrapping(
            config: SoftCryptoServiceConfig,
            wrappingKeyMap: SoftWrappingKeyMap
        ): SoftPrivateKeyWrapping = when (config.wrapping.name) {
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

        private fun createSoftKeyMap(
            config: SoftCryptoServiceConfig,
            wrapping: SoftPrivateKeyWrapping
        ): SoftKeyMap = when (config.keyMap.name) {
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