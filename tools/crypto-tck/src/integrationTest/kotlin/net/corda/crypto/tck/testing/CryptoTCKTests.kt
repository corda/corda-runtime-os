package net.corda.crypto.tck.testing

import net.corda.crypto.tck.ComplianceTestType
import net.corda.crypto.tck.testing.hsms.AllWrappedKeysHSMProvider
import net.corda.crypto.tck.CryptoTCK
import net.corda.crypto.tck.testing.hsms.AllAliasedKeysHSMConfiguration
import net.corda.crypto.tck.testing.hsms.AllAliasedKeysHSMProvider
import net.corda.crypto.tck.testing.hsms.AllWrappedKeysHSMConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.time.Duration

@ExtendWith(ServiceExtension::class)
class CryptoTCKTests {
    companion object {
        @InjectService(timeout = 5000L)
        lateinit var tck: CryptoTCK
    }

    @Test
    fun `TCK should be able to test AllWrappedKeysHSM`() {
        tck.builder(AllWrappedKeysHSMProvider.NAME, AllWrappedKeysHSMConfiguration("corda"))
            .withConcurrency(4)
            .withServiceTimeout(Duration.ofSeconds(30))
            .withSessionTimeout(Duration.ofSeconds(1))
            .withTestSuites(ComplianceTestType.CRYPTO_SERVICE, ComplianceTestType.SESSION_INACTIVITY)
            .build()
            .run()
    }

    @Test
    fun `TCK should be able to test AllAliasedKeysHSM`() {
        tck.builder(AllAliasedKeysHSMProvider.NAME, AllAliasedKeysHSMConfiguration("corda"))
            .withConcurrency(4)
            .withServiceTimeout(Duration.ofSeconds(30))
            .withSessionTimeout(Duration.ofSeconds(1))
            .withTestSuites(ComplianceTestType.CRYPTO_SERVICE, ComplianceTestType.SESSION_INACTIVITY)
            .build()
            .run()
    }
}