package net.corda.crypto.tck.testing.hsms

import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceProvider
import org.osgi.service.component.annotations.Component

@Component(service = [CryptoServiceProvider::class])
class AllWrappedKeysHSMProvider : CryptoServiceProvider<AllWrappedKeysHSMProvider.Configuration> {

    class Configuration(val userName: String)

    companion object {
        const val NAME = "AllWrappedKeysHSMProvider"
    }

    override val configType: Class<Configuration> = Configuration::class.java

    override val name: String = NAME

    override fun getInstance(config: Configuration): CryptoService = AllWrappedKeysHSM(config.userName)
}