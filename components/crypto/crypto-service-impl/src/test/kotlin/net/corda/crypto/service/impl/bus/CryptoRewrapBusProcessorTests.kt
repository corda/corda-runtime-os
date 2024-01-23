package net.corda.crypto.service.impl.bus

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.crypto.core.CryptoService
import net.corda.data.crypto.wire.ops.key.rotation.IndividualKeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyType
import net.corda.data.crypto.wire.ops.key.status.UnmanagedKeyStatus
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.UUID

class CryptoRewrapBusProcessorTests {
    companion object {
        private val tenantId = UUID.randomUUID().toString()
        private const val OLD_PARENT_KEY_ALIAS = "alias1"
    }

    private val serializer = mock<CordaAvroSerializer<UnmanagedKeyStatus>> {
        on { serialize(any()) } doReturn byteArrayOf(42)
    }
    private val deserializer = mock<CordaAvroDeserializer<UnmanagedKeyStatus>> {
        on { deserialize(any()) } doReturn UnmanagedKeyStatus(OLD_PARENT_KEY_ALIAS, 10, 5)
    }
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<UnmanagedKeyStatus>() } doReturn serializer
        on { createAvroDeserializer<UnmanagedKeyStatus>(any(), any()) } doReturn deserializer
    }

    private val cryptoService: CryptoService = mock<CryptoService> { }
    private val stateManager = mock<StateManager> {
        on { get(any()) } doReturn mapOf(
            OLD_PARENT_KEY_ALIAS + tenantId + "keyRotation" to State(
                OLD_PARENT_KEY_ALIAS + tenantId + "keyRotation",
                "random".toByteArray(),
                0,
                Metadata(mapOf("status" to "In Progress"))
            )
        )
    }

    private val cryptoRewrapBusProcessor = CryptoRewrapBusProcessor(
        cryptoService,
        stateManager,
        cordaAvroSerializationFactory
    )

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
                        OLD_PARENT_KEY_ALIAS,
                        "root2",
                        "alias1",
                        null,
                        KeyType.UNMANAGED
                    )
                )
            )
        )
        verify(cryptoService, times(1)).rewrapWrappingKey(any(), any(), any())
        verify(stateManager, times(1)).update(any())
    }
}
