package net.corda.cipher.suite.impl.platform.registration

import net.corda.cipher.suite.impl.platform.PlatformCipherSuiteMetadata
import net.corda.cipher.suite.impl.platform.handling.PlatformDigestHandler
import net.corda.cipher.suite.impl.platform.handling.PlatformGenerateKeyHandler
import net.corda.cipher.suite.impl.platform.handling.PlatformSignDataHandler
import net.corda.cipher.suite.impl.platform.handling.PlatformVerifySignatureHandler
import net.corda.cipher.suite.impl.platform.handling.SoftKeyMap
import net.corda.v5.cipher.suite.CryptoWorkerCipherSuite
import net.corda.v5.cipher.suite.handlers.CipherSuiteRegistrar
import net.corda.v5.cipher.suite.handlers.CryptoWorkerCipherSuiteRegistrar
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking

// Must have the lifecycle management as it depends on the SoftKeyMap

@ServiceRanking(0)
@Component(service = [CipherSuiteRegistrar::class])
class PlatformCryptoWorkerCipherSuiteRegistrar @Activate constructor(
    @Reference(service = SoftKeyMap::class)
    softKeyMap: SoftKeyMap
) : CryptoWorkerCipherSuiteRegistrar {
    private val suiteMetadata = PlatformCipherSuiteMetadata()
    private val verifyHandler = PlatformVerifySignatureHandler(suiteMetadata)
    private val digestHandler = PlatformDigestHandler(suiteMetadata)
    private val generateKeyHandler = PlatformGenerateKeyHandler(suiteMetadata, softKeyMap)
    private val signDataHandler = PlatformSignDataHandler(suiteMetadata, softKeyMap)

    override fun registerWith(suite: CryptoWorkerCipherSuite) {
        suite.register(suiteMetadata.secureRandom)
        suiteMetadata.digests.forEach {
            suite.register(it.algorithmName, digestHandler)
        }
        suiteMetadata.supportedSigningSchemes.values.forEach { keyScheme ->
            suite.register(keyScheme, suiteMetadata, verifyHandler, generateKeyHandler, signDataHandler)
        }
    }
}