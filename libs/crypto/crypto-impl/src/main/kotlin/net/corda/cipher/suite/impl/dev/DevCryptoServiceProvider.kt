package net.corda.cipher.suite.impl.dev

import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.CryptoServiceProvider

class DevCryptoServiceProvider : CryptoServiceProvider<DevCryptoServiceConfiguration> {
    companion object {
        const val SERVICE_NAME = "dev"
    }

    override val name: String = SERVICE_NAME

    override val configType: Class<DevCryptoServiceConfiguration> = DevCryptoServiceConfiguration::class.java

    override fun getInstance(context: CryptoServiceContext<DevCryptoServiceConfiguration>): CryptoService {
        TODO("Not yet implemented")
    }
}