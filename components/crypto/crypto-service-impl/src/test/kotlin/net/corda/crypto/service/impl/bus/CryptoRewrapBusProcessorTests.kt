package net.corda.crypto.service.impl.bus

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.crypto.core.CryptoService
import net.corda.data.crypto.wire.ops.key.rotation.IndividualKeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyType
import net.corda.data.crypto.wire.ops.key.status.UnmanagedKeyStatus
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.UUID

class CryptoRewrapBusProcessorTests {
    private lateinit var cryptoRewrapBusProcessor: CryptoRewrapBusProcessor
    private lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory

    companion object {
        private val cryptoService: CryptoService = mock<CryptoService> { }
        private val tenantId = UUID.randomUUID().toString()
    }

    @BeforeEach
    fun setup() {
        val serializer = mock<CordaAvroSerializer<UnmanagedKeyStatus>> {
            on { serialize(any()) } doReturn byteArrayOf(42)
        }
        cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
            on { createAvroSerializer<UnmanagedKeyStatus>() } doReturn serializer
        }

        cryptoRewrapBusProcessor = CryptoRewrapBusProcessor(
            cryptoService,
            mock(),
            cordaAvroSerializationFactory
        )
    }

    @Test
    fun `do a mocked rewrap`() {
        cryptoRewrapBusProcessor.onNext(
            listOf(
                Record(
                    "TBC",
                    UUID.randomUUID().toString(),
                    IndividualKeyRotationRequest(
                        UUID.randomUUID().toString(),
                        tenantId,
                        "alias1",
                        "root2",
                        "alias1",
                        KeyType.UNMANAGED
                    )
                )
            )
        )
        verify(cryptoService, times(1)).rewrapWrappingKey(any(), any(), any())
    }
}
