package net.corda.crypto.impl.cipher.suite.registrars

import net.corda.crypto.impl.cipher.suite.PlatformCipherSuiteMetadata
import net.corda.crypto.impl.cipher.suite.handling.PlatformDigestHandler
import net.corda.crypto.impl.cipher.suite.handling.PlatformVerifySignatureHandler
import net.corda.v5.cipher.suite.CipherSuite
import net.corda.v5.cipher.suite.handlers.CipherSuiteRegistrar
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@ServiceRanking(0)
@Component(service = [CipherSuiteRegistrar::class])
class PlatformCipherSuiteRegistrar : CipherSuiteRegistrar {
    private val suiteMetadata = PlatformCipherSuiteMetadata()
    private val verifyHandler = PlatformVerifySignatureHandler(suiteMetadata)
    private val digestHandler = PlatformDigestHandler(suiteMetadata)

    override fun registerWith(suite: CipherSuite) {
        suite.register(suiteMetadata.secureRandom)
        suiteMetadata.digests.forEach {
            suite.register(it.algorithmName, digestHandler)
        }
        suiteMetadata.supportedSigningSchemes.values.forEach { keyScheme ->
            suite.register(keyScheme, suiteMetadata, verifyHandler)
        }
    }
}