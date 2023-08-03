package net.corda.crypto.service.impl.bus

import net.corda.crypto.core.CryptoService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.UUID

class CryptoRewrapBusProcessorTests{
    companion object {
        private val cryptoService: CryptoService = mock<CryptoService> { }
        private val cryptoRewrapBusProcessor = CryptoRewrapBusProcessor(cryptoService)
        private val tenantId = UUID.randomUUID().toString()
        private val targetAlias = "alpha"
        private val parentKeyAlias = "beta"
    }

    @Test
    fun `do nothing`(){
        //cryptoRewrapBusProcessor.onNext()

    }


}