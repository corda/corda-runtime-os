package net.corda.crypto.testkit

import net.corda.crypto.core.service.DigestService
import net.corda.crypto.core.service.KeyEncodingService
import net.corda.crypto.impl.cipher.suite.CipherSuiteImpl
import net.corda.crypto.impl.cipher.suite.PlatformCipherSuiteRegistrar
import net.corda.crypto.impl.service.DigestServiceImpl
import net.corda.crypto.impl.service.KeyEncodingServiceImpl
import net.corda.v5.cipher.suite.CipherSuite
import java.security.SecureRandom

class CryptoTestKit {
    private val suite: CipherSuite by lazy(LazyThreadSafetyMode.PUBLICATION) {
        CipherSuiteImpl(
            listOf(PlatformCipherSuiteRegistrar())
        )
    }

    val secureRandom: SecureRandom by lazy(LazyThreadSafetyMode.PUBLICATION) { suite.secureRandom }

    val keyEncoder: KeyEncodingService by lazy(LazyThreadSafetyMode.PUBLICATION) { KeyEncodingServiceImpl(suite) }

    val digestService: DigestService by lazy(LazyThreadSafetyMode.PUBLICATION) { DigestServiceImpl(suite, null) }
}