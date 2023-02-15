package net.corda.membership.impl.httprpc.v1

import net.corda.membership.client.dto.MemberInfoSubmittedDto
import net.corda.membership.client.dto.RegistrationRequestStatusDto
import net.corda.membership.client.dto.RegistrationStatusDto
import net.corda.membership.httprpc.v1.types.response.MemberInfoSubmitted
import net.corda.membership.httprpc.v1.types.response.RestRegistrationRequestStatus
import net.corda.membership.httprpc.v1.types.response.RegistrationStatus
import net.corda.membership.impl.rest.v1.fromDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant

class ClientDtoConvertersTests {
    @Test
    fun `fromDto for RegistrationRequestStatusDto`() {
        val dto = RegistrationRequestStatusDto(
            "id",
            Instant.ofEpochSecond(10),
            Instant.ofEpochSecond(20),
            RegistrationStatusDto.PENDING_MANUAL_APPROVAL,
            MemberInfoSubmittedDto(
                mapOf(
                    "key 1" to "value 1",
                    "key 2" to "value 2",
                )
            )
        )

        val status = dto.fromDto()

        assertThat(status).isEqualTo(
            RestRegistrationRequestStatus(
                "id",
                Instant.ofEpochSecond(10),
                Instant.ofEpochSecond(20),
                RegistrationStatus.PENDING_MANUAL_APPROVAL,
                MemberInfoSubmitted(
                    mapOf(
                        "key 1" to "value 1",
                        "key 2" to "value 2",
                    )
                )
            )
        )
    }

    @ParameterizedTest
    @EnumSource(RegistrationStatusDto::class)
    fun `fromDto for RegistrationStatusDto`(type: RegistrationStatusDto) {
        val status = type.fromDto()

        assertThat(status.name).isEqualTo(type.name)
    }
}