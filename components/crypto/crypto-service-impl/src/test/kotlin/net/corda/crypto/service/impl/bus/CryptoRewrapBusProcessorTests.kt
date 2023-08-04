package net.corda.crypto.service.impl.bus

import net.corda.crypto.core.CryptoService
import net.corda.data.crypto.wire.ops.rewrap.CryptoRewrapRequest
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.UUID

class CryptoRewrapBusProcessorTests {
    companion object {
        private val cryptoService: CryptoService = mock<CryptoService> { }
        private val cryptoRewrapBusProcessor = CryptoRewrapBusProcessor(cryptoService)
        private val tenantId = UUID.randomUUID().toString()
    }

    @Test
    fun `do a mocked rewrap`() {
        cryptoRewrapBusProcessor.onNext(
            listOf(
                Record(
                    "TBC",
                    UUID.randomUUID().toString(),
                    CryptoRewrapRequest(tenantId, "alias1", "root2")
                )
            )
        )
    }
}