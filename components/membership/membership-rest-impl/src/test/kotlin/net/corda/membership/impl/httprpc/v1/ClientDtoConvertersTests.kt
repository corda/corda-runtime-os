package net.corda.membership.impl.httprpc.v1

import net.corda.membership.client.dto.MemberInfoSubmittedDto
import net.corda.membership.client.dto.RegistrationRequestProgressDto
import net.corda.membership.client.dto.RegistrationRequestStatusDto
import net.corda.membership.client.dto.RegistrationStatusDto
import net.corda.membership.client.dto.SubmittedRegistrationStatus
import net.corda.membership.httprpc.v1.types.response.MemberInfoSubmitted
import net.corda.membership.httprpc.v1.types.response.RestRegistrationRequestStatus
import net.corda.membership.httprpc.v1.types.response.RegistrationStatus
import net.corda.membership.impl.rest.v1.fromDto
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant

class ClientDtoConvertersTests {
    private companion object {
        const val REASON = "test reason"
    }

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
            ),
            REASON
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
                ),
                REASON
            )
        )
    }

    @ParameterizedTest
    @EnumSource(RegistrationStatusDto::class)
    fun `fromDto for RegistrationStatusDto`(type: RegistrationStatusDto) {
        val status = type.fromDto()

        assertThat(status.name).isEqualTo(type.name)
    }

    @Test
    fun `fromDto for RegistrationRequestProgressDto`() {
        val dto = RegistrationRequestProgressDto(
            "id",
            Instant.ofEpochMilli(10),
            SubmittedRegistrationStatus.SUBMITTED,
            true,
            "reason",
            MemberInfoSubmittedDto(mapOf("key" to "value"))
        )

        val status = dto.fromDto()

        assertSoftly {
            assertThat(status.registrationId).isEqualTo("id")
            assertThat(status.registrationStatus).isEqualTo("SUBMITTED")
            assertThat(status.registrationSent).isEqualTo(Instant.ofEpochMilli(10))
            assertThat(status.availableNow).isTrue
            assertThat(status.reason).isEqualTo("reason")
            assertThat(status.memberInfoSubmitted.data)
                .hasSize(1)
                .containsEntry(
                    "key",
                    "value",
                )
        }
    }
}