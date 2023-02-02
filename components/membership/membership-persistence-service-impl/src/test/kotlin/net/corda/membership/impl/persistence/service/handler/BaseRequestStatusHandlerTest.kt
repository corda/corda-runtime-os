package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
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
import java.time.Instant

class BaseRequestStatusHandlerTest {
    private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>>()
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
        val context = byteArrayOf(1, 2, 3)
        whenever(keyValuePairListDeserializer.deserialize(context)).doReturn(
            KeyValuePairList(
                listOf(
                    KeyValuePair("key", "value"),
                    KeyValuePair("registrationProtocolVersion", "12")
                )
            )
        )
        val entity = RegistrationRequestEntity(
            "id",
            "short-hash",
            "PENDING_MGM_NETWORK_ACCESS",
            Instant.ofEpochSecond(500),
            Instant.ofEpochSecond(600),
            byteArrayOf(1, 2, 3)
        )

        val details = with(handler) {
            entity.toDetails()
        }

        assertSoftly {softly ->
            softly.assertThat(details.registrationId).isEqualTo("id")
            softly.assertThat(details.registrationSent).isEqualTo(Instant.ofEpochSecond(500))
            softly.assertThat(details.registrationLastModified).isEqualTo(Instant.ofEpochSecond(600))
            softly.assertThat(details.registrationStatus).isEqualTo(RegistrationStatus.PENDING_MGM_NETWORK_ACCESS)
            softly.assertThat(details.registrationProtocolVersion).isEqualTo(12)
            softly.assertThat(details.memberProvidedContext).isEqualTo(
                KeyValuePairList(
                    listOf(
                        KeyValuePair("key", "value"),
                        KeyValuePair("registrationProtocolVersion", "12")
                    )
                )
            )
        }
    }

    @Test
    fun `toDetails throws exception for invalid status`() {
        val context = byteArrayOf(1, 2, 3)
        whenever(keyValuePairListDeserializer.deserialize(context)).doReturn(
            KeyValuePairList(
                listOf(
                    KeyValuePair("key", "value"),
                    KeyValuePair("registrationProtocolVersion", "12")
                )
            )
        )
        val entity = RegistrationRequestEntity(
            "id",
            "short-hash",
            "Nop",
            Instant.ofEpochSecond(500),
            Instant.ofEpochSecond(600),
            byteArrayOf(1, 2, 3)
        )

        assertThrows<MembershipPersistenceException> {
            with(handler) {
                entity.toDetails()
            }
        }
    }

    @Test
    fun `toDetails gets the protocol version from the context`() {
        val context = byteArrayOf(1, 2, 3)
        whenever(keyValuePairListDeserializer.deserialize(context)).doReturn(
            KeyValuePairList(
                listOf(
                    KeyValuePair("key", "value"),
                    KeyValuePair("registrationProtocolVersion", "122")
                )
            )
        )
        val entity = RegistrationRequestEntity(
            "id",
            "short-hash",
            "PENDING_MGM_NETWORK_ACCESS",
            Instant.ofEpochSecond(500),
            Instant.ofEpochSecond(600),
            byteArrayOf(1, 2, 3)
        )

        val details = with(handler) {
            entity.toDetails()
        }

        assertThat(details.registrationProtocolVersion).isEqualTo(122)
    }

    @Test
    fun `toDetails return default protocol version if context is not a number`() {
        val context = byteArrayOf(1, 2, 3)
        whenever(keyValuePairListDeserializer.deserialize(context)).doReturn(
            KeyValuePairList(
                listOf(
                    KeyValuePair("key", "value"),
                    KeyValuePair("registrationProtocolVersion", "a")
                )
            )
        )
        val entity = RegistrationRequestEntity(
            "id",
            "short-hash",
            "PENDING_MGM_NETWORK_ACCESS",
            Instant.ofEpochSecond(500),
            Instant.ofEpochSecond(600),
            byteArrayOf(1, 2, 3)
        )

        val details = with(handler) {
            entity.toDetails()
        }

        assertThat(details.registrationProtocolVersion).isEqualTo(1)
    }

    @Test
    fun `toDetails return default protocol version if context version number is null`() {
        val context = byteArrayOf(1, 2, 3)
        whenever(keyValuePairListDeserializer.deserialize(context)).doReturn(
            KeyValuePairList(
                listOf(
                    KeyValuePair("key", "value"),
                    KeyValuePair("registrationProtocolVersion", null)
                )
            )
        )
        val entity = RegistrationRequestEntity(
            "id",
            "short-hash",
            "PENDING_MGM_NETWORK_ACCESS",
            Instant.ofEpochSecond(500),
            Instant.ofEpochSecond(600),
            byteArrayOf(1, 2, 3)
        )

        val details = with(handler) {
            entity.toDetails()
        }

        assertThat(details.registrationProtocolVersion).isEqualTo(1)
    }

    @Test
    fun `toDetails return default protocol version if context has no version`() {
        val context = byteArrayOf(1, 2, 3)
        whenever(keyValuePairListDeserializer.deserialize(context)).doReturn(
            KeyValuePairList(
                listOf(
                    KeyValuePair("key", "value"),
                )
            )
        )
        val entity = RegistrationRequestEntity(
            "id",
            "short-hash",
            "PENDING_MGM_NETWORK_ACCESS",
            Instant.ofEpochSecond(500),
            Instant.ofEpochSecond(600),
            byteArrayOf(1, 2, 3)
        )

        val details = with(handler) {
            entity.toDetails()
        }

        assertThat(details.registrationProtocolVersion).isEqualTo(1)
    }

    @Test
    fun `toDetails return default protocol version if context is null`() {
        val context = byteArrayOf(1, 2, 3)
        whenever(keyValuePairListDeserializer.deserialize(context)).doReturn(KeyValuePairList())
        val entity = RegistrationRequestEntity(
            "id",
            "short-hash",
            "PENDING_MGM_NETWORK_ACCESS",
            Instant.ofEpochSecond(500),
            Instant.ofEpochSecond(600),
            byteArrayOf(1, 2, 3)
        )

        val details = with(handler) {
            entity.toDetails()
        }

        assertThat(details.registrationProtocolVersion).isEqualTo(1)
    }
}