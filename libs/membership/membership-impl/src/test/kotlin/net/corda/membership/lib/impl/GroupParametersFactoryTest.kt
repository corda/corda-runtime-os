package net.corda.membership.lib.impl

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.membership.lib.SignedGroupParameters
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.membership.GroupParameters
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
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

    private val clock = TestClock(Instant.ofEpochSecond(100))

    private val groupParametersValues = mapOf(
        EPOCH_KEY to EPOCH,
        MPV_KEY to MPV,
        MODIFIED_TIME_KEY to clock.instant().toString()
    )
    private val mockLayeredPropertyMap: LayeredPropertyMap = mock {
        on { it.parse(EPOCH_KEY, Int::class.java) } doReturn EPOCH.toInt()
        on { it.parse(MPV_KEY, Int::class.java) } doReturn MPV.toInt()
        on { it.parse(MODIFIED_TIME_KEY, Instant::class.java) } doReturn clock.instant()
        on { entries } doReturn groupParametersValues.entries
    }
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory = mock {
        on { createMap(any()) } doReturn mockLayeredPropertyMap
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
    private val cordaAvroSerializer: CordaAvroSerializer<KeyValuePairList> = mock {
        on { serialize(any()) } doReturn serialisedGroupParameters
    }
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn cordaAvroDeserializer
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn cordaAvroSerializer
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
            val params = groupParametersFactory.create(
                KeyValuePairList(groupParametersValues.entries.map { KeyValuePair(it.key, it.value) })
            )
            assertThat(params.entries)
                .containsExactlyElementsOf(groupParametersValues.entries)
        }
    }

    @Nested
    inner class FromSignedGroupParametersTest {

        @Test
        fun `if the avro group parameters are submitted, the factory returns a signed group parameters instance`() {
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
            assertThat(groupParameters.entries)
                .containsExactlyElementsOf(groupParametersValues.entries)
            assertThat(groupParameters.bytes).isEqualTo(serialisedGroupParameters)
            assertThat((groupParameters as SignedGroupParameters).signature.context).isEqualTo(sigContext)
            assertThat(groupParameters.signature.by).isEqualTo(pubKey)
            assertThat(groupParameters.signature.bytes).isEqualTo(sigBytes)
        }
    }

    private val ByteArray.buffer: ByteBuffer
        get() = ByteBuffer.wrap(this)
}
