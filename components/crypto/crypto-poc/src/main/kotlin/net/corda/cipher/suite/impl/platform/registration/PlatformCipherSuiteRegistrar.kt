package net.corda.cipher.suite.impl.platform.registration

import net.corda.cipher.suite.impl.platform.PlatformCipherSuiteMetadata
import net.corda.cipher.suite.impl.platform.handling.PlatformDigestHandler
import net.corda.cipher.suite.impl.platform.handling.PlatformVerifySignatureHandler
import net.corda.v5.cipher.suite.CipherSuite
import net.corda.v5.cipher.suite.providers.CipherSuiteRegistrar
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@ServiceRanking(0)
@Component(service = [CipherSuiteRegistrar::class])
class PlatformCipherSuiteRegistrar : CipherSuiteRegistrar {
    private val suiteMetadata = PlatformCipherSuiteMetadata()
    private val verifyHandler = PlatformVerifySignatureHandler(suiteMetadata)
    private val digestHandler = PlatformDigestHandler(suiteMetadata)

    override fun registerWith(suite: CipherSuite) {
        suiteMetadata.digests.forEach {
            suite.register(it.algorithmName, digestHandler)
        }
        suiteMetadata.supportedSigningSchemes.values.forEach { keyScheme ->
            suite.register(keyScheme, suiteMetadata, verifyHandler)
        }
    }
}