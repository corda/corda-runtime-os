package net.corda.crypto.tck.testing

import net.corda.crypto.tck.testing.hsms.AllWrappedKeysHSMProvider
import net.corda.v5.crypto.tck.CryptoTCK
import net.corda.v5.crypto.tck.ExecutionOptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path


@ExtendWith(ServiceExtension::class)
class CryptoTCKTests {
    companion object {
        @InjectService(timeout = 5000L)
        lateinit var tck: CryptoTCK
    }

    @Test
    fun `TCK should be able to test AllWrappedKeysHSM`() {
        tck.run(ExecutionOptions(
            serviceName = AllWrappedKeysHSMProvider.NAME,
            testResultsDirectory = Path.of("", AllWrappedKeysHSMProvider::class.simpleName).toAbsolutePath()
        ))
    }
}