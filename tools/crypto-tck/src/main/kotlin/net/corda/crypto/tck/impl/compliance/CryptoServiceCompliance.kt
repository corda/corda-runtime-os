package net.corda.crypto.tck.impl.compliance

import net.corda.crypto.tck.impl.CryptoServiceProviderMap
import net.corda.crypto.tck.ExecutionOptionsExtension
import net.corda.v5.base.util.contextLogger
import net.corda.crypto.tck.ExecutionOptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class, ExecutionOptionsExtension::class)
class CryptoServiceCompliance {
    companion object {
        private val logger = contextLogger()

        @InjectService(timeout = 5000L)
        lateinit var providers: CryptoServiceProviderMap
    }

    lateinit var options: ExecutionOptions

    @BeforeEach
    fun setup(options: ExecutionOptions) {
        this.options = options
        logger.info("serviceName=${options.serviceName}")
    }

    @Test
    fun `Blank CryptoService test`() {
        logger.info("We have providers: [${providers.all().joinToString { it.name }}]")
    }
}