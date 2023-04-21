package net.corda.membership.lib.registration

import net.corda.data.membership.common.RegistrationStatus
import net.corda.membership.lib.registration.RegistrationStatusExt.canMoveToStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class RegistrationStatusExtTest {
    @ParameterizedTest
    @EnumSource(RegistrationStatus::class)
    fun `canMoveToStatus return true for the same state`(status: RegistrationStatus) {
        assertThat(status.canMoveToStatus(status)).isTrue
    }

    @Test
    fun `canMoveToStatus return true when the status comes after this status`() {
        assertThat(RegistrationStatus.NEW.canMoveToStatus(RegistrationStatus.SENT_TO_MGM)).isTrue
    }

    @Test
    fun `canMoveToStatus return false when the status comes before this status`() {
        assertThat(RegistrationStatus.SENT_TO_MGM.canMoveToStatus(RegistrationStatus.NEW)).isFalse
    }
}
