package net.corda.membership.lib

import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class VersionedMessageBuilderTest {
    private companion object {
        val registrationId = UUID.randomUUID().toString()
    }

    @Test
    fun `SetOwnRegistrationStatus version 1 messages are built as expected`() {
        with(retrieveRegistrationStatusMessage(50001, registrationId, RegistrationStatus.APPROVED.name)) {
            assertThat(this).isInstanceOf(SetOwnRegistrationStatus::class.java)
            val message = this as SetOwnRegistrationStatus
            assertThat(message.newStatus).isInstanceOf(RegistrationStatus::class.java)
            assertThat(message.newStatus.name).isEqualTo(RegistrationStatus.APPROVED.name)
        }
    }

    @Test
    fun `SetOwnRegistrationStatus version 2 messages are built as expected`() {
        with(retrieveRegistrationStatusMessage(50101, registrationId, RegistrationStatusV2.APPROVED.name)) {
            assertThat(this).isInstanceOf(SetOwnRegistrationStatusV2::class.java)
            val message = this as SetOwnRegistrationStatusV2
            assertThat(message.newStatus).isInstanceOf(RegistrationStatusV2::class.java)
            assertThat(message.newStatus.name).isEqualTo(RegistrationStatusV2.APPROVED.name)
        }
    }

    @Test
    fun `exception is being thrown when not expected status needs to be distributed`() {
        assertThrows<IllegalArgumentException> {
            retrieveRegistrationStatusMessage(50101, registrationId, RegistrationStatusV2.NEW.name)
        }
    }

    @Test
    fun `exception is being thrown when not expected platform version is being used`() {
        assertThrows<IllegalArgumentException> {
            retrieveRegistrationStatusMessage(634896, registrationId, RegistrationStatusV2.NEW.name)
        }
    }
}