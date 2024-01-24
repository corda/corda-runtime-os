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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
    fun `unmanaged rewrap calls rewrapWrappingKey in crypto service`() {
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

    @Test
    fun `unmanaged rewrap with null tenant Id should be ignored`() {
        assertTrue(
            cryptoRewrapBusProcessor.onNext(
                listOf(
                    Record(
                        "TBC",
                        UUID.randomUUID().toString(),
                        IndividualKeyRotationRequest(
                            UUID.randomUUID().toString(),
                            null,
                            OLD_PARENT_KEY_ALIAS,
                            "root2",
                            "alias1",
                            null,
                            KeyType.UNMANAGED
                        )
                    )
                )
            ).isEmpty()
        )

        verify(cryptoService, never()).rewrapWrappingKey(any(), any(), any())
        verify(stateManager, never()).update(any())
    }

    @Test
    fun `unmanaged rewrap with empty tenant Id should be ignored`() {
        assertTrue(
            cryptoRewrapBusProcessor.onNext(
                listOf(
                    Record(
                        "TBC",
                        UUID.randomUUID().toString(),
                        IndividualKeyRotationRequest(
                            UUID.randomUUID().toString(),
                            "",
                            OLD_PARENT_KEY_ALIAS,
                            "root2",
                            "alias1",
                            null,
                            KeyType.UNMANAGED
                        )
                    )
                )
            ).isEmpty()
        )

        verify(cryptoService, never()).rewrapWrappingKey(any(), any(), any())
        verify(stateManager, never()).update(any())
    }

    @Test
    fun `unmanaged rewrap with null old parent alias should be ignored`() {
        assertTrue(
            cryptoRewrapBusProcessor.onNext(
                listOf(
                    Record(
                        "TBC",
                        UUID.randomUUID().toString(),
                        IndividualKeyRotationRequest(
                            UUID.randomUUID().toString(),
                            tenantId,
                            null,
                            "root2",
                            "alias1",
                            null,
                            KeyType.UNMANAGED
                        )
                    )
                )
            ).isEmpty()
        )

        verify(cryptoService, never()).rewrapWrappingKey(any(), any(), any())
        verify(stateManager, never()).update(any())
    }

    @Test
    fun `unmanaged rewrap with empty old parent alias should be ignored`() {
        assertTrue(
            cryptoRewrapBusProcessor.onNext(
                listOf(
                    Record(
                        "TBC",
                        UUID.randomUUID().toString(),
                        IndividualKeyRotationRequest(
                            UUID.randomUUID().toString(),
                            tenantId,
                            "",
                            "root2",
                            "alias1",
                            null,
                            KeyType.UNMANAGED
                        )
                    )
                )
            ).isEmpty()
        )

        verify(cryptoService, never()).rewrapWrappingKey(any(), any(), any())
        verify(stateManager, never()).update(any())
    }

    @Test
    fun `unmanaged rewrap with null new parent alias should be ignored`() {
        assertTrue(
            cryptoRewrapBusProcessor.onNext(
                listOf(
                    Record(
                        "TBC",
                        UUID.randomUUID().toString(),
                        IndividualKeyRotationRequest(
                            UUID.randomUUID().toString(),
                            tenantId,
                            OLD_PARENT_KEY_ALIAS,
                            null,
                            "alias1",
                            null,
                            KeyType.UNMANAGED
                        )
                    )
                )
            ).isEmpty()
        )

        verify(cryptoService, never()).rewrapWrappingKey(any(), any(), any())
        verify(stateManager, never()).update(any())
    }

    @Test
    fun `unmanaged rewrap with empty new parent alias should be ignored`() {
        assertTrue(
            cryptoRewrapBusProcessor.onNext(
                listOf(
                    Record(
                        "TBC",
                        UUID.randomUUID().toString(),
                        IndividualKeyRotationRequest(
                            UUID.randomUUID().toString(),
                            tenantId,
                            OLD_PARENT_KEY_ALIAS,
                            "",
                            "alias1",
                            null,
                            KeyType.UNMANAGED
                        )
                    )
                )
            ).isEmpty()
        )

        verify(cryptoService, never()).rewrapWrappingKey(any(), any(), any())
        verify(stateManager, never()).update(any())
    }

    @Test
    fun `unmanaged rewrap with null target alias should be ignored`() {
        assertTrue(
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
                            null,
                            null,
                            KeyType.UNMANAGED
                        )
                    )
                )
            ).isEmpty()
        )

        verify(cryptoService, never()).rewrapWrappingKey(any(), any(), any())
        verify(stateManager, never()).update(any())
    }

    @Test
    fun `unmanaged rewrap with empty target alias should be ignored`() {
        assertTrue(
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
                            "",
                            "",
                            KeyType.UNMANAGED
                        )
                    )
                )
            ).isEmpty()
        )

        verify(cryptoService, never()).rewrapWrappingKey(any(), any(), any())
        verify(stateManager, never()).update(any())
    }

    @Test
    fun `unmanaged rewrap with key uuid set should be ignored`() {
        assertTrue(
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
                            UUID.randomUUID().toString(),
                            KeyType.UNMANAGED
                        )
                    )
                )
            ).isEmpty()
        )

        verify(cryptoService, never()).rewrapWrappingKey(any(), any(), any())
        verify(stateManager, never()).update(any())
    }

    @Test
    fun `managed rewrap calls rewrapAllSigningKeysWrappedBy in crypto service`() {
        val uuid = UUID.randomUUID()
        cryptoRewrapBusProcessor.onNext(
            listOf(
                Record(
                    "TBC",
                    UUID.randomUUID().toString(),
                    IndividualKeyRotationRequest(
                        UUID.randomUUID().toString(),
                        tenantId,
                        null,
                        null,
                        null,
                        uuid.toString(),
                        KeyType.MANAGED
                    )
                )
            )
        )
        verify(cryptoService, times(1)).rewrapAllSigningKeysWrappedBy(uuid, tenantId)
    }

    @Test
    fun `managed rewrap with null tenant Id should be ignored`() {
        assertTrue(
            cryptoRewrapBusProcessor.onNext(
                listOf(
                    Record(
                        "TBC",
                        UUID.randomUUID().toString(),
                        IndividualKeyRotationRequest(
                            UUID.randomUUID().toString(),
                            null,
                            null,
                            null,
                            null,
                            UUID.randomUUID().toString(),
                            KeyType.MANAGED
                        )
                    )
                )
            ).isEmpty()
        )

        verify(cryptoService, never()).rewrapWrappingKey(any(), any(), any())
        verify(stateManager, never()).update(any())
    }

    @Test
    fun `managed rewrap with empty tenant Id should be ignored`() {
        assertTrue(
            cryptoRewrapBusProcessor.onNext(
                listOf(
                    Record(
                        "TBC",
                        UUID.randomUUID().toString(),
                        IndividualKeyRotationRequest(
                            UUID.randomUUID().toString(),
                            "",
                            null,
                            null,
                            null,
                            UUID.randomUUID().toString(),
                            KeyType.MANAGED
                        )
                    )
                )
            ).isEmpty()
        )

        verify(cryptoService, never()).rewrapWrappingKey(any(), any(), any())
        verify(stateManager, never()).update(any())
    }

    @Test
    fun `managed rewrap with old parent key alias set should be ignored`() {
        assertTrue(
            cryptoRewrapBusProcessor.onNext(
                listOf(
                    Record(
                        "TBC",
                        UUID.randomUUID().toString(),
                        IndividualKeyRotationRequest(
                            UUID.randomUUID().toString(),
                            tenantId,
                            OLD_PARENT_KEY_ALIAS,
                            null,
                            null,
                            UUID.randomUUID().toString(),
                            KeyType.MANAGED
                        )
                    )
                )
            ).isEmpty()
        )

        verify(cryptoService, never()).rewrapWrappingKey(any(), any(), any())
        verify(stateManager, never()).update(any())
    }

    @Test
    fun `managed rewrap with new parent key alias set should be ignored`() {
        assertTrue(
            cryptoRewrapBusProcessor.onNext(
                listOf(
                    Record(
                        "TBC",
                        UUID.randomUUID().toString(),
                        IndividualKeyRotationRequest(
                            UUID.randomUUID().toString(),
                            tenantId,
                            null,
                            "root2",
                            null,
                            UUID.randomUUID().toString(),
                            KeyType.MANAGED
                        )
                    )
                )
            ).isEmpty()
        )

        verify(cryptoService, never()).rewrapWrappingKey(any(), any(), any())
        verify(stateManager, never()).update(any())
    }

    @Test
    fun `managed rewrap with target key alias set should be ignored`() {
        assertTrue(
            cryptoRewrapBusProcessor.onNext(
                listOf(
                    Record(
                        "TBC",
                        UUID.randomUUID().toString(),
                        IndividualKeyRotationRequest(
                            UUID.randomUUID().toString(),
                            tenantId,
                            null,
                            null,
                            "alias1",
                            UUID.randomUUID().toString(),
                            KeyType.MANAGED
                        )
                    )
                )
            ).isEmpty()
        )

        verify(cryptoService, never()).rewrapWrappingKey(any(), any(), any())
        verify(stateManager, never()).update(any())
    }

    @Test
    fun `managed rewrap with null uuid should be ignored`() {
        assertTrue(
            cryptoRewrapBusProcessor.onNext(
                listOf(
                    Record(
                        "TBC",
                        UUID.randomUUID().toString(),
                        IndividualKeyRotationRequest(
                            UUID.randomUUID().toString(),
                            tenantId,
                            null,
                            null,
                            null,
                            null,
                            KeyType.MANAGED
                        )
                    )
                )
            ).isEmpty()
        )

        verify(cryptoService, never()).rewrapWrappingKey(any(), any(), any())
        verify(stateManager, never()).update(any())
    }

    @Test
    fun `managed rewrap with empty uuid should be ignored`() {
        assertTrue(
            cryptoRewrapBusProcessor.onNext(
                listOf(
                    Record(
                        "TBC",
                        UUID.randomUUID().toString(),
                        IndividualKeyRotationRequest(
                            UUID.randomUUID().toString(),
                            tenantId,
                            null,
                            null,
                            null,
                            "",
                            KeyType.MANAGED
                        )
                    )
                )
            ).isEmpty()
        )

        verify(cryptoService, never()).rewrapWrappingKey(any(), any(), any())
        verify(stateManager, never()).update(any())
    }

    @Test
    fun `managed rewrap with invalid uuid should be ignored`() {
        assertTrue(
            cryptoRewrapBusProcessor.onNext(
                listOf(
                    Record(
                        "TBC",
                        UUID.randomUUID().toString(),
                        IndividualKeyRotationRequest(
                            UUID.randomUUID().toString(),
                            tenantId,
                            null,
                            null,
                            null,
                            "invalid uuid",
                            KeyType.MANAGED
                        )
                    )
                )
            ).isEmpty()
        )

        verify(cryptoService, never()).rewrapWrappingKey(any(), any(), any())
        verify(stateManager, never()).update(any())
    }
}
