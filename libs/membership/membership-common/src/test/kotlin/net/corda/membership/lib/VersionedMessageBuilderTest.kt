package net.corda.membership.lib

import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.membership.lib.VersionedMessageBuilder.retrieveRegistrationStatusMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class VersionedMessageBuilderTest {
    private companion object {
        val registrationId = UUID.randomUUID().toString()
    }

    @Test
    fun `SetOwnRegistrationStatus version 1 messages are built as expected`() {
        RegistrationStatus.values().forEach { status ->
            with(retrieveRegistrationStatusMessage(50001, registrationId, status.name, "some reason")) {
                assertThat(this).isInstanceOf(SetOwnRegistrationStatus::class.java)
                val message = this as SetOwnRegistrationStatus
                assertThat(message.newStatus).isInstanceOf(RegistrationStatus::class.java)
                assertThat(message.newStatus.name).isEqualTo(status.name)
            }
        }
    }

    @Test
    fun `SetOwnRegistrationStatus version 2 messages are built as expected`() {
        RegistrationStatusV2.values().forEach { status ->
            val reason = "some reason"
            with(retrieveRegistrationStatusMessage(50101, registrationId, status.name, "some reason")) {
                assertThat(this).isInstanceOf(SetOwnRegistrationStatusV2::class.java)
                val message = this as SetOwnRegistrationStatusV2
                assertThat(message.newStatus).isInstanceOf(RegistrationStatusV2::class.java)
                assertThat(message.newStatus.name).isEqualTo(status.name)
                assertThat(message.reason).isEqualTo(reason)
            }
        }
    }

    @Test
    fun `null is returned when not expected status needs to be distributed`() {
        assertThat(retrieveRegistrationStatusMessage(50101, registrationId, "dummyStatus", "some reason")).isNull()
    }
}
