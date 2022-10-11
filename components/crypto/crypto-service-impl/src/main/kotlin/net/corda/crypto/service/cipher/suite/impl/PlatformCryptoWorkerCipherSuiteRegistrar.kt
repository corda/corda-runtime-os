package net.corda.crypto.service.cipher.suite.impl

import net.corda.crypto.impl.cipher.suite.handling.PlatformCipherSuiteMetadataImpl
import net.corda.crypto.impl.cipher.suite.handling.PlatformDigestHandler
import net.corda.crypto.impl.cipher.suite.handling.PlatformVerifySignatureHandler
import net.corda.crypto.service.cipher.suite.SoftKeyMap
import net.corda.v5.cipher.suite.CryptoWorkerCipherSuite
import net.corda.v5.cipher.suite.handlers.CipherSuiteRegistrar
import net.corda.v5.cipher.suite.handlers.CryptoWorkerCipherSuiteRegistrar
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking

// TODO: Must have the lifecycle management as it depends on the SoftKeyMap

@ServiceRanking(0)
@Component(service = [CipherSuiteRegistrar::class])
class PlatformCryptoWorkerCipherSuiteRegistrar @Activate constructor(
    @Reference(service = SoftKeyMap::class)
    softKeyMap: SoftKeyMap
) : CryptoWorkerCipherSuiteRegistrar {
    private val suiteMetadata = PlatformCipherSuiteMetadataImpl()
    private val verifyHandler = PlatformVerifySignatureHandler(suiteMetadata)
    private val digestHandler = PlatformDigestHandler(suiteMetadata)
    private val generateKeyHandler = PlatformGenerateKeyHandler(suiteMetadata, softKeyMap)
    private val signDataHandler = PlatformSignDataHandler(suiteMetadata, softKeyMap)

    override fun registerWith(suite: CryptoWorkerCipherSuite) {
        suite.register(suiteMetadata.secureRandom)
        (suiteMetadata.digests.map { it.algorithmName } + PlatformDigestHandler.customDigestAlgorithms).distinct()
            .forEach {
                suite.register(it, digestHandler)
            }
        suiteMetadata.supportedSigningSchemes.values.forEach { keyScheme ->
            suite.register(keyScheme, suiteMetadata, verifyHandler, generateKeyHandler, signDataHandler)
        }
    }
}