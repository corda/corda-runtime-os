package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.common.RegistrationStatus
import net.corda.membership.db.lib.RegistrationStatusHelper.toStatus
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class RegistrationStatusHelperTest {
    @ParameterizedTest
    @EnumSource(RegistrationStatus::class)
    fun `toStatus return the correct status`(status: RegistrationStatus) {
        val convertedStatus = status.name.lowercase().toStatus()

        assertThat(convertedStatus).isEqualTo(status)
    }

    @Test
    fun `toStatus throw exception if the status can not be found`() {
        assertThrows<MembershipPersistenceException> {
            "test".toStatus()
        }
    }
}
