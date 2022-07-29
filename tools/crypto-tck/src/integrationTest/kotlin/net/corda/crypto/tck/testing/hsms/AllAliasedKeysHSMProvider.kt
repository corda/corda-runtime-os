package net.corda.crypto.tck.testing.hsms

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.ConfigurationSecrets
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceProvider
import net.corda.v5.crypto.DigestService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CryptoServiceProvider::class])
class AllAliasedKeysHSMProvider @Activate constructor(
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = DigestService::class)
    private val digestService: DigestService
) : CryptoServiceProvider<AllAliasedKeysHSMConfiguration> {
    companion object {
        const val NAME = "AllAliasedKeysHSM"
    }

    override val configType: Class<AllAliasedKeysHSMConfiguration> = AllAliasedKeysHSMConfiguration::class.java

    override val name: String = NAME

    override fun getInstance(config: AllAliasedKeysHSMConfiguration, secrets: ConfigurationSecrets): CryptoService =
        AllAliasedKeysHSM(config, schemeMetadata, digestService)
}