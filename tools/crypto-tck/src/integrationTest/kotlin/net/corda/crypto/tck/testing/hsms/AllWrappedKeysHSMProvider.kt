package net.corda.crypto.tck.testing.hsms

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.ConfigurationSecrets
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceProvider
import net.corda.v5.cipher.suite.PlatformDigestService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CryptoServiceProvider::class])
class AllWrappedKeysHSMProvider @Activate constructor(
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = PlatformDigestService::class)
    private val digestService: PlatformDigestService
) : CryptoServiceProvider<AllWrappedKeysHSMConfiguration> {
    companion object {
        const val NAME = "AllWrappedKeysHSM"
    }

    override val configType: Class<AllWrappedKeysHSMConfiguration> = AllWrappedKeysHSMConfiguration::class.java

    override val name: String = NAME

    override fun getInstance(config: AllWrappedKeysHSMConfiguration, secrets: ConfigurationSecrets): CryptoService =
        AllWrappedKeysHSM(config, schemeMetadata, digestService)
}