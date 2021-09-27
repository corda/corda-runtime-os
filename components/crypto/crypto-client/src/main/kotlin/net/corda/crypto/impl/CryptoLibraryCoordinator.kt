package net.corda.crypto.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.CryptoLibraryClientsFactoryProvider
import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.crypto.impl.dev.DevCryptoServiceProvider
import net.corda.crypto.impl.lifecycle.AbstractCryptoCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_CODE_NAME
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(immediate = true)
open class CryptoLibraryCoordinator @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = CipherSuiteFactory::class)
    private val cipherSuiteFactory: CipherSuiteFactory,
    @Reference(service = CryptoLibraryClientsFactoryProvider::class)
    private val cryptoFactoryProvider: CryptoLibraryClientsFactoryProvider
) : AbstractCryptoCoordinator(
    coordinatorFactory,
    configurationReadService,
    listOf(
        cipherSuiteFactory,
        cryptoFactoryProvider
    )
) {
    companion object {
        val devConfig by lazy(LazyThreadSafetyMode.PUBLICATION) {
            CryptoLibraryConfigImpl(
                mapOf(
                    "isDev" to "true",
                    "keyCache" to emptyMap<String, Any?>(),
                    "mngCache" to emptyMap<String, Any?>(),
                    "rpc" to emptyMap<String, Any?>(),
                    "default" to mapOf<String, Any?>(
                        "default" to mapOf<String, Any?>(
                            "serviceName" to DevCryptoServiceProvider.SERVICE_NAME,
                            "defaultSignatureScheme" to EDDSA_ED25519_CODE_NAME
                        )
                    )
                )
            )
        }
    }

    override fun handleEmptyCryptoConfig(): CryptoLibraryConfig = devConfig
}