package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant

class BaseRequestStatusHandlerTest {
    private companion object {
        const val REGISTRATION_ID = "id"
        const val HOLDING_ID_HASH = "short-hash"
        const val REASON = "test reason"
        const val SERIAL = 0L
        const val SIGNATURE_SPEC = "signatureSpec"
        const val PROTOCOL_VERSION = 12

        val sent = Instant.ofEpochSecond(500)
        val modified = Instant.ofEpochSecond(600)
        val context = byteArrayOf(1, 2, 3)
        val signatureKey = byteArrayOf(4)
        val signatureContent = byteArrayOf(5)

        val entity = RegistrationRequestEntity(
            REGISTRATION_ID,
            HOLDING_ID_HASH,
            RegistrationStatus.SENT_TO_MGM.toString(),
            sent,
            modified,
            context,
            signatureKey,
            signatureContent,
            SIGNATURE_SPEC,
            SERIAL,
            REASON,
        )

        val deserializedContext = KeyValuePairList(
            listOf(
                KeyValuePair("key", "value"),
                KeyValuePair("registrationProtocolVersion", PROTOCOL_VERSION.toString())
            )
        )
    }

    private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>> {
        on { deserialize(context) } doReturn deserializedContext
    }
    private val serializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn keyValuePairListDeserializer
    }
    private val persistenceHandlerServices = mock<PersistenceHandlerServices> {
        on { cordaAvroSerializationFactory } doReturn serializationFactory
    }
    private val handler = object: BaseRequestStatusHandler<String, String>(persistenceHandlerServices) {
        override fun invoke(context: MembershipRequestContext, request: String): String {
            // Do nothing...
            return ""
        }
    }

    @Test
    fun `toDetails returns the correct details`() {
        val details = with(handler) {
            entity.toDetails()
        }

        assertSoftly {softly ->
            softly.assertThat(details.registrationId).isEqualTo(REGISTRATION_ID)
            softly.assertThat(details.registrationSent).isEqualTo(sent)
            softly.assertThat(details.registrationLastModified).isEqualTo(modified)
            softly.assertThat(details.registrationStatus).isEqualTo(RegistrationStatus.SENT_TO_MGM)
            softly.assertThat(details.registrationProtocolVersion).isEqualTo(PROTOCOL_VERSION)
            softly.assertThat(details.memberProvidedContext).isEqualTo(deserializedContext)
            softly.assertThat(details.memberSignature)
                .isEqualTo(CryptoSignatureWithKey(ByteBuffer.wrap(signatureKey), ByteBuffer.wrap(signatureContent)))
            softly.assertThat(details.memberSignatureSpec)
                .isEqualTo(CryptoSignatureSpec(SIGNATURE_SPEC, null, null))
            softly.assertThat(details.reason).isEqualTo(REASON)
        }
    }

    @Test
    fun `toDetails returns empty signature spec when the name was empty`() {
        val emptySpec = ""
        val entityWithEmptySignatureSpec = RegistrationRequestEntity(
            REGISTRATION_ID,
            HOLDING_ID_HASH,
            RegistrationStatus.SENT_TO_MGM.toString(),
            sent,
            modified,
            context,
            signatureKey,
            signatureContent,
            emptySpec,
            SERIAL,
            REASON,
        )

        val details = with(handler) {
            entityWithEmptySignatureSpec.toDetails()
        }
        assertThat(details.memberSignatureSpec)
            .isEqualTo(CryptoSignatureSpec(emptySpec, null, null))
    }

    @Test
    fun `toDetails throws exception for invalid status`() {
        val entityWithInvalidStatus = RegistrationRequestEntity(
            REGISTRATION_ID,
            HOLDING_ID_HASH,
            "Nop",
            sent,
            modified,
            context,
            signatureKey,
            signatureContent,
            SIGNATURE_SPEC,
            SERIAL,
        )

        assertThrows<MembershipPersistenceException> {
            with(handler) {
                entityWithInvalidStatus.toDetails()
            }
        }
    }

    @Test
    fun `toDetails gets the protocol version from the context`() {
        whenever(keyValuePairListDeserializer.deserialize(context)).doReturn(
            KeyValuePairList(
                listOf(
                    KeyValuePair("key", "value"),
                    KeyValuePair("registrationProtocolVersion", "122")
                )
            )
        )

        val details = with(handler) {
            entity.toDetails()
        }

        assertThat(details.registrationProtocolVersion).isEqualTo(122)
    }

    @Test
    fun `toDetails return default protocol version if context is not a number`() {
        whenever(keyValuePairListDeserializer.deserialize(context)).doReturn(
            KeyValuePairList(
                listOf(
                    KeyValuePair("key", "value"),
                    KeyValuePair("registrationProtocolVersion", "a")
                )
            )
        )

        val details = with(handler) {
            entity.toDetails()
        }

        assertThat(details.registrationProtocolVersion).isEqualTo(1)
    }

    @Test
    fun `toDetails return default protocol version if context version number is null`() {
        whenever(keyValuePairListDeserializer.deserialize(context)).doReturn(
            KeyValuePairList(
                listOf(
                    KeyValuePair("key", "value"),
                    KeyValuePair("registrationProtocolVersion", null)
                )
            )
        )

        val details = with(handler) {
            entity.toDetails()
        }

        assertThat(details.registrationProtocolVersion).isEqualTo(1)
    }

    @Test
    fun `toDetails return default protocol version if context has no version`() {
        whenever(keyValuePairListDeserializer.deserialize(context)).doReturn(
            KeyValuePairList(
                listOf(
                    KeyValuePair("key", "value"),
                )
            )
        )

        val details = with(handler) {
            entity.toDetails()
        }

        assertThat(details.registrationProtocolVersion).isEqualTo(1)
    }

    @Test
    fun `toDetails return default protocol version if context is null`() {
        whenever(keyValuePairListDeserializer.deserialize(context)).doReturn(KeyValuePairList())

        val details = with(handler) {
            entity.toDetails()
        }

        assertThat(details.registrationProtocolVersion).isEqualTo(1)
    }

    @Test
    fun `toDetails sets reason to null by default`() {
        whenever(keyValuePairListDeserializer.deserialize(context)).doReturn(KeyValuePairList())
        val entity = RegistrationRequestEntity(
            REGISTRATION_ID,
            HOLDING_ID_HASH,
            RegistrationStatus.SENT_TO_MGM.toString(),
            sent,
            modified,
            context,
            signatureKey,
            signatureContent,
            SIGNATURE_SPEC,
            SERIAL,
        )

        val details = with(handler) {
            entity.toDetails()
        }

        assertThat(details.reason).isNull()
    }
}