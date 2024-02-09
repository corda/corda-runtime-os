package net.corda.crypto.service.impl.bus

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.KeyRotationMetadataValues
import net.corda.crypto.core.KeyRotationStatus
import net.corda.data.crypto.wire.ops.key.rotation.IndividualKeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyType
import net.corda.data.crypto.wire.ops.key.status.ManagedKeyStatus
import net.corda.data.crypto.wire.ops.key.status.UnmanagedKeyStatus
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.time.Instant
import java.util.UUID

class CryptoRewrapBusProcessorTests {
    private lateinit var unmanagedKeysSerialized: MutableList<UnmanagedKeyStatus>
    private lateinit var unmanagedSerializer: CordaAvroSerializer<UnmanagedKeyStatus>
    private lateinit var unmanagedDeserializer: CordaAvroDeserializer<UnmanagedKeyStatus>
    private lateinit var unmanagedCordaAvroSerializationFactory: CordaAvroSerializationFactory
    private lateinit var unmanagedCryptoRewrapBusProcessor: CryptoRewrapBusProcessor
    private lateinit var managedKeysSerialized: MutableList<ManagedKeyStatus>
    private lateinit var managedSerializer: CordaAvroSerializer<ManagedKeyStatus>
    private lateinit var managedDeserializer: CordaAvroDeserializer<ManagedKeyStatus>
    private lateinit var managedCordaAvroSerializationFactory: CordaAvroSerializationFactory
    private lateinit var cryptoService: CryptoService
    private lateinit var stateManagerUpdateCapture: KArgumentCaptor<Collection<State>>
    private lateinit var stateManagerDeleteCapture: KArgumentCaptor<Collection<State>>
    private lateinit var stateManager: StateManager
    private lateinit var managedCryptoRewrapBusProcessor: CryptoRewrapBusProcessor

    companion object {
        private val tenantId = UUID.randomUUID().toString()
        private const val OLD_PARENT_KEY_ALIAS = "alias1"
        private const val WRAPPING_KEY_ALIAS = "alias"
        private const val DEFAULT_MASTER_WRAP_KEY_ALIAS = "defaultKeyAlias"
    }

    @BeforeEach
    fun setup() {
        unmanagedKeysSerialized = mutableListOf()
        unmanagedSerializer = mock<CordaAvroSerializer<UnmanagedKeyStatus>> {
            on { serialize(any()) } doAnswer { args ->
                unmanagedKeysSerialized.add(args.arguments[0] as UnmanagedKeyStatus)
                byteArrayOf(42)
            }
        }
        unmanagedDeserializer = mock<CordaAvroDeserializer<UnmanagedKeyStatus>> {
            on { deserialize(any()) } doReturn UnmanagedKeyStatus(
                tenantId,
                10,
                5,
                Instant.now()
            )
        }
        unmanagedCordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
            on { createAvroSerializer<UnmanagedKeyStatus>() } doReturn unmanagedSerializer
            on { createAvroDeserializer<UnmanagedKeyStatus>(any(), any()) } doReturn unmanagedDeserializer
        }

        managedKeysSerialized = mutableListOf()
        managedSerializer = mock<CordaAvroSerializer<ManagedKeyStatus>> {
            on { serialize(any()) } doAnswer { args ->
                managedKeysSerialized.add(args.arguments[0] as ManagedKeyStatus)
                byteArrayOf(42)
            }
        }
        managedDeserializer = mock<CordaAvroDeserializer<ManagedKeyStatus>> {
            on { deserialize(any()) } doReturn ManagedKeyStatus(
                WRAPPING_KEY_ALIAS,
                10,
                5,
                Instant.now()
            )
        }
        managedCordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
            on { createAvroSerializer<ManagedKeyStatus>() } doReturn managedSerializer
            on { createAvroDeserializer<ManagedKeyStatus>(any(), any()) } doReturn managedDeserializer
        }

        cryptoService = mock {
            on { rewrapAllSigningKeysWrappedBy(any(), any()) } doReturn 5
        }
        stateManagerUpdateCapture = argumentCaptor()
        stateManagerDeleteCapture = argumentCaptor()
        stateManager = mock<StateManager> {
            on { get(any()) } doReturn mapOf(
                DEFAULT_MASTER_WRAP_KEY_ALIAS + tenantId + "keyRotation" to State(
                    DEFAULT_MASTER_WRAP_KEY_ALIAS + tenantId + "keyRotation",
                    "random".toByteArray(),
                    0,
                    Metadata(mapOf(KeyRotationMetadataValues.STATUS to KeyRotationStatus.IN_PROGRESS))
                )
            )
            on { update(stateManagerUpdateCapture.capture()) } doReturn emptyMap()
            on { delete(stateManagerDeleteCapture.capture()) } doReturn emptyMap()
        }

        unmanagedCryptoRewrapBusProcessor = CryptoRewrapBusProcessor(
            cryptoService,
            stateManager,
            unmanagedCordaAvroSerializationFactory,
            DEFAULT_MASTER_WRAP_KEY_ALIAS
        )

        managedCryptoRewrapBusProcessor = CryptoRewrapBusProcessor(
            cryptoService,
            stateManager,
            managedCordaAvroSerializationFactory,
            DEFAULT_MASTER_WRAP_KEY_ALIAS
        )
    }

    @Test
    fun `unmanaged rewrap calls rewrapWrappingKey in crypto service`() {
        unmanagedCryptoRewrapBusProcessor.onNext(
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
            unmanagedCryptoRewrapBusProcessor.onNext(
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
            unmanagedCryptoRewrapBusProcessor.onNext(
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
    fun `unmanaged rewrap with null target alias should be ignored`() {
        assertTrue(
            unmanagedCryptoRewrapBusProcessor.onNext(
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
            unmanagedCryptoRewrapBusProcessor.onNext(
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
            unmanagedCryptoRewrapBusProcessor.onNext(
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
        managedCryptoRewrapBusProcessor.onNext(
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
            managedCryptoRewrapBusProcessor.onNext(
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
            managedCryptoRewrapBusProcessor.onNext(
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
    fun `managed rewrap with target key alias set should be ignored`() {
        assertTrue(
            managedCryptoRewrapBusProcessor.onNext(
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
            managedCryptoRewrapBusProcessor.onNext(
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
            managedCryptoRewrapBusProcessor.onNext(
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
            managedCryptoRewrapBusProcessor.onNext(
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

    @Test
    fun `managed key rotation rewraps all keys and writes state`() {
        val uuid = UUID.randomUUID()
        managedCryptoRewrapBusProcessor.onNext(
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
        verify(stateManager, times(1)).get(any())
        verify(stateManager, times(1)).update(any())

        assertThat(stateManagerUpdateCapture.firstValue).size().isEqualTo(1)
        stateManagerUpdateCapture.firstValue.forEachIndexed { index, it ->
            assertThat(it.metadata[KeyRotationMetadataValues.STATUS]).isEqualTo(KeyRotationStatus.DONE)

            val managedKeyStatus = (managedKeysSerialized[index] as? ManagedKeyStatus)
            assertThat(managedKeyStatus).isNotNull()
            assertThat(managedKeyStatus!!.wrappingKeyAlias).isEqualTo(WRAPPING_KEY_ALIAS)
            assertThat(managedKeyStatus.rotatedKeys).isEqualTo(10)
            assertThat(managedKeyStatus.total).isEqualTo(10)
        }
    }
}
