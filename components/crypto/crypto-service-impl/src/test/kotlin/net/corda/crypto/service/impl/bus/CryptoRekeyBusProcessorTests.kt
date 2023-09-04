package net.corda.crypto.service.impl.bus

import net.corda.crypto.core.CryptoService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class CryptoRekeyBusProcessorTests {

    companion object {
        private val cryptoService: CryptoService = mock<CryptoService> { }
        private val cryptoRekeyBusProcessor = CryptoRekeyBusProcessor(cryptoService)
    }

    @Test
    fun `do a mocked key rotation`() {
        // to be implemented
    }
}
