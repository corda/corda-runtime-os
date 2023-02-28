package net.corda.membership.lib.impl

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.lib.toMap
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.membership.GroupParameters
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant
import net.corda.data.membership.SignedGroupParameters as AvroGroupParameters

class GroupParametersFactoryTest {
    private companion object {
        const val MPV = "5000"
        const val EPOCH = "1"
    }

    private val groupParametersCaptor = argumentCaptor<Map<String, String>>()
    private val groupParametersValues = mapOf(
        EPOCH_KEY to EPOCH,
        MPV_KEY to MPV,
        MODIFIED_TIME_KEY to Instant.now().toString()
    )
    private val mockLayeredPropertyMap: LayeredPropertyMap = mock {
        on { it.parse(EPOCH_KEY, Int::class.java) } doReturn EPOCH.toInt()
        on { it.parse(MPV_KEY, Int::class.java) } doReturn MPV.toInt()
        on { it.parse(MODIFIED_TIME_KEY, Instant::class.java) } doReturn Instant.now()
        on { entries } doReturn groupParametersValues.entries
    }
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory = mock {
        on { createMap(groupParametersCaptor.capture()) } doReturn mockLayeredPropertyMap
    }

    private val deserialisedGroupParameters = mock<KeyValuePairList> {
        on { items } doReturn groupParametersValues.map { KeyValuePair(it.key, it.value) }
    }
    private val serialisedGroupParameters = byteArrayOf(9, 8, 7)
    private val pubKeyBytes = "pub-key".toByteArray()
    private val pubKey = mock<PublicKey>()
    private val keyEncodingService: KeyEncodingService = mock {
        on { decodePublicKey(pubKeyBytes) } doReturn pubKey
    }
    private val cordaAvroDeserializer: CordaAvroDeserializer<KeyValuePairList> = mock {
        on { deserialize(serialisedGroupParameters) } doReturn deserialisedGroupParameters
    }
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn cordaAvroDeserializer
    }

    private val groupParametersFactory = GroupParametersFactoryImpl(
        layeredPropertyMapFactory,
        keyEncodingService,
        cordaAvroSerializationFactory
    )

    @Nested
    inner class FromKeyValuePairListTest {
        @Test
        fun `factory creating GroupParameters`() {
            val entries = KeyValuePairList(
                listOf(
                    KeyValuePair(EPOCH_KEY, EPOCH),
                    KeyValuePair(MPV_KEY, MPV),
                    KeyValuePair(MODIFIED_TIME_KEY, Instant.now().toString())
                )
            )

            groupParametersFactory.create(entries)

            assertThat(groupParametersCaptor.firstValue).isEqualTo(entries.toMap())
        }

        @Test
        fun `factory successfully creates and returns GroupParameters`() {
            val entries = KeyValuePairList(
                listOf(
                    KeyValuePair(EPOCH_KEY, EPOCH),
                    KeyValuePair(MPV_KEY, MPV),
                    KeyValuePair(MODIFIED_TIME_KEY, Instant.now().toString())
                )
            )

            val groupParameters = groupParametersFactory.create(entries)

            with(groupParameters) {
                assertThat(epoch).isEqualTo(EPOCH.toInt())
                assertThat(minimumPlatformVersion).isEqualTo(MPV.toInt())
                assertThat(modifiedTime).isBeforeOrEqualTo(Instant.now())
            }
        }
    }

    @Nested
    inner class FromSignedGroupParametersTest {

        @Test
        fun `if the avro group parameters does not have signature information, the factory returns a group parameters instance`() {
            val avro = mock<AvroGroupParameters> {
                on { mgmSignature } doReturn null
                on { groupParameters } doReturn serialisedGroupParameters.buffer
            }
            val groupParameters = groupParametersFactory.create(avro)

            assertThat(groupParameters).isInstanceOf(GroupParameters::class.java)
            assertThat(groupParameters).isNotInstanceOf(SignedGroupParameters::class.java)
            assertThat(groupParameters.entries).containsExactlyElementsOf(groupParametersValues.entries)
        }

        @Test
        fun `if the avro group parameters have signature information, the factory returns a signed group parameters instance`() {
            val sigBytes = "sig-bytes".toByteArray()
            val sigContext = mapOf("sig-context-key" to "sig-context-value")
            val sig = mock<CryptoSignatureWithKey> {
                on { publicKey } doReturn pubKeyBytes.buffer
                on { context } doReturn KeyValuePairList(sigContext.map { KeyValuePair(it.key, it.value) })
                on { bytes } doReturn sigBytes.buffer
            }
            val avro = mock<AvroGroupParameters> {
                on { mgmSignature } doReturn sig
                on { groupParameters } doReturn serialisedGroupParameters.buffer
            }
            val groupParameters = groupParametersFactory.create(avro)

            assertThat(groupParameters).isInstanceOf(GroupParameters::class.java)
            assertThat(groupParameters).isInstanceOf(SignedGroupParameters::class.java)
            assertThat(groupParametersCaptor.firstValue.entries).containsExactlyElementsOf(groupParametersValues.entries)
            assertThat((groupParameters as SignedGroupParameters).bytes).isEqualTo(serialisedGroupParameters)
            assertThat(groupParameters.signature.context).isEqualTo(sigContext)
            assertThat(groupParameters.signature.by).isEqualTo(pubKey)
            assertThat(groupParameters.signature.bytes).isEqualTo(sigBytes)
        }
    }

    private val ByteArray.buffer: ByteBuffer
        get() = ByteBuffer.wrap(this)
}
