package net.corda.membership.impl.httprpc.v1

import net.corda.httprpc.exception.BadRequestException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.exception.ServiceUnavailableException
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.client.CouldNotFindMemberException
import net.corda.membership.client.MemberResourceClient
import net.corda.membership.client.RegistrationProgressNotFoundException
import net.corda.membership.client.ServiceNotReadyException
import net.corda.membership.client.dto.MemberInfoSubmittedDto
import net.corda.membership.client.dto.RegistrationRequestProgressDto
import net.corda.membership.client.dto.RegistrationRequestStatusDto
import net.corda.membership.client.dto.RegistrationStatusDto
import net.corda.membership.client.dto.SubmittedRegistrationStatus
import net.corda.membership.httprpc.v1.types.request.MemberRegistrationRequest
import net.corda.membership.impl.rest.v1.MemberRegistrationRestResourceImpl
import net.corda.membership.impl.rest.v1.fromDto
import net.corda.membership.impl.rest.v1.toDto
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import net.corda.test.util.time.TestClock
import net.corda.virtualnode.ShortHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import java.lang.Exception
import java.time.Instant
import kotlin.test.assertFailsWith

class MemberRegistrationRestResourceTest {
    companion object {
        private const val HOLDING_IDENTITY_ID = "1234567890ab"
        private const val INVALID_HOLDING_IDENTITY_ID = "${HOLDING_IDENTITY_ID}00"
        private val holdingIdShortHash = ShortHash.of(HOLDING_IDENTITY_ID)
        private const val ACTION = "requestJoin"
        private val clock = TestClock(Instant.ofEpochSecond(100))
    }

    private var coordinatorIsRunning = false
    private val coordinator: LifecycleCoordinator = mock {
        on { isRunning } doAnswer { coordinatorIsRunning }
        on { start() } doAnswer { coordinatorIsRunning = true }
        on { stop() } doAnswer { coordinatorIsRunning = false }
    }

    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }

    private val registrationProgress = RegistrationRequestProgressDto(
        "RequestId",
        clock.instant(),
        SubmittedRegistrationStatus.SUBMITTED,
        true,
        "",
        MemberInfoSubmittedDto(emptyMap())
    )

    private val memberResourceClient: MemberResourceClient = mock {
        on { startRegistration(any()) } doReturn registrationProgress
    }

    private val memberRegistrationRestResource = MemberRegistrationRestResourceImpl(
        lifecycleCoordinatorFactory,
        memberResourceClient,
    )

    private val registrationRequest = MemberRegistrationRequest(
        ACTION,
        context = mock()
    )

    @Test
    fun `starting and stopping the service succeeds`() {
        memberRegistrationRestResource.start()
        assertTrue(memberRegistrationRestResource.isRunning)
        memberRegistrationRestResource.stop()
        assertFalse(memberRegistrationRestResource.isRunning)
    }

    @Test
    fun `starting registration calls the client svc`() {
        memberRegistrationRestResource.start()
        memberRegistrationRestResource.activate("")
        memberRegistrationRestResource.startRegistration(HOLDING_IDENTITY_ID, registrationRequest)
        verify(memberResourceClient).startRegistration(eq(registrationRequest.toDto(HOLDING_IDENTITY_ID)))
        memberRegistrationRestResource.deactivate("")
        memberRegistrationRestResource.stop()
    }

    @Test
    fun `starting registration with invalid member will fail`() {
        memberRegistrationRestResource.start()
        memberRegistrationRestResource.activate("")
        whenever(memberResourceClient.startRegistration(any())).doThrow(CouldNotFindMemberException(holdingIdShortHash))

        assertThrows<ResourceNotFoundException> {
            memberRegistrationRestResource.startRegistration(HOLDING_IDENTITY_ID, registrationRequest)
        }
    }

    @Test
    fun `startRegistration fails when service is not running`() {
        val ex = assertFailsWith<ServiceUnavailableException> {
            memberRegistrationRestResource.startRegistration(HOLDING_IDENTITY_ID, registrationRequest)
        }
        assertThat(ex).hasMessage("MemberRegistrationRestResourceImpl is not running. Operation cannot be fulfilled.")
    }

    @Test
    fun `startRegistration throws bad request if short hash is invalid`() {
        memberRegistrationRestResource.start()
        memberRegistrationRestResource.activate("")

        assertFailsWith<BadRequestException> {
            memberRegistrationRestResource.startRegistration(INVALID_HOLDING_IDENTITY_ID, registrationRequest)
        }

        memberRegistrationRestResource.deactivate("")
        memberRegistrationRestResource.stop()
    }

    @Test
    fun `checkRegistrationProgress fails when service is not running`() {
        val ex = assertFailsWith<ServiceUnavailableException> {
            memberRegistrationRestResource.checkRegistrationProgress(HOLDING_IDENTITY_ID)
        }
        assertThat(ex).hasMessage("MemberRegistrationRestResourceImpl is not running. Operation cannot be fulfilled.")
    }
    @Test
    fun `checkSpecificRegistrationProgress fails when service is not running`() {
        val ex = assertFailsWith<ServiceUnavailableException> {
            memberRegistrationRestResource.checkSpecificRegistrationProgress(HOLDING_IDENTITY_ID, "id")
        }
        assertThat(ex).hasMessage("MemberRegistrationRestResourceImpl is not running. Operation cannot be fulfilled.")
    }

    @Test
    fun `checkRegistrationProgress returns the correct data`() {
        val data = (1..3).map {
            RegistrationRequestStatusDto(
                "id $it",
                Instant.ofEpochSecond(20L + it),
                Instant.ofEpochSecond(200L + it),
                RegistrationStatusDto.APPROVED,
                MemberInfoSubmittedDto(mapOf("key $it" to "value"))
            )
        }
        whenever(memberResourceClient.checkRegistrationProgress(holdingIdShortHash)).doReturn(data)
        memberRegistrationRestResource.start()
        memberRegistrationRestResource.activate("")

        val status = memberRegistrationRestResource.checkRegistrationProgress(HOLDING_IDENTITY_ID)

        assertThat(status)
            .hasSize(data.size)
            .containsAnyElementsOf(data.map { it.fromDto() })
    }

    @Test
    fun `checkRegistrationProgress throw 404 when member can not be found`() {
        whenever(
            memberResourceClient.checkRegistrationProgress(holdingIdShortHash)
        ).doThrow(CouldNotFindMemberException(holdingIdShortHash))
        memberRegistrationRestResource.start()
        memberRegistrationRestResource.activate("")

        assertThrows<ResourceNotFoundException> {
            memberRegistrationRestResource.checkRegistrationProgress(HOLDING_IDENTITY_ID)
        }
    }

    @Test
    fun `checkRegistrationProgress throw 503 when the database is not read`() {
        whenever(memberResourceClient.checkRegistrationProgress(holdingIdShortHash)).doThrow(
            ServiceNotReadyException(
                Exception("")
            )
        )
        memberRegistrationRestResource.start()
        memberRegistrationRestResource.activate("")

        assertThrows<ServiceUnavailableException> {
            memberRegistrationRestResource.checkRegistrationProgress(HOLDING_IDENTITY_ID)
        }
    }

    @Test
    fun `checkSpecificRegistrationProgress returns the correct data`() {
        val data = RegistrationRequestStatusDto(
            "id",
            Instant.ofEpochSecond(20L),
            Instant.ofEpochSecond(200L),
            RegistrationStatusDto.APPROVED,
            MemberInfoSubmittedDto(mapOf("key" to "value"))
        )

        whenever(memberResourceClient.checkSpecificRegistrationProgress(holdingIdShortHash, "id")).doReturn(data)
        memberRegistrationRestResource.start()
        memberRegistrationRestResource.activate("")

        val status = memberRegistrationRestResource.checkSpecificRegistrationProgress(HOLDING_IDENTITY_ID, "id")

        assertThat(status)
            .isEqualTo(data.fromDto())
    }

    @Test
    fun `checkSpecificRegistrationProgress throw 404 when the member can not be found`() {
        whenever(memberResourceClient.checkSpecificRegistrationProgress(holdingIdShortHash, "id"))
            .doThrow(CouldNotFindMemberException(holdingIdShortHash))
        memberRegistrationRestResource.start()
        memberRegistrationRestResource.activate("")

        assertThrows<ResourceNotFoundException> {
            memberRegistrationRestResource.checkSpecificRegistrationProgress(HOLDING_IDENTITY_ID, "id")
        }
    }

    @Test
    fun `checkSpecificRegistrationProgress throw 404 when the request can not be found`() {
        whenever(memberResourceClient.checkSpecificRegistrationProgress(holdingIdShortHash, "id"))
            .doThrow(RegistrationProgressNotFoundException(""))
        memberRegistrationRestResource.start()
        memberRegistrationRestResource.activate("")

        assertThrows<ResourceNotFoundException> {
            memberRegistrationRestResource.checkSpecificRegistrationProgress(HOLDING_IDENTITY_ID, "id")
        }
    }

    @Test
    fun `checkSpecificRegistrationProgress throw 503 when the db is not ready`() {
        whenever(memberResourceClient.checkSpecificRegistrationProgress(holdingIdShortHash, "id"))
            .doThrow(ServiceNotReadyException(Exception("")))
        memberRegistrationRestResource.start()
        memberRegistrationRestResource.activate("")

        assertThrows<ServiceUnavailableException> {
            memberRegistrationRestResource.checkSpecificRegistrationProgress(HOLDING_IDENTITY_ID, "id")
        }
    }

    @Test
    fun `checkSpecificRegistrationProgress throws bad request if short hash is invalid`() {
        memberRegistrationRestResource.start()
        memberRegistrationRestResource.activate("")

        assertFailsWith<BadRequestException> {
            memberRegistrationRestResource.checkSpecificRegistrationProgress(INVALID_HOLDING_IDENTITY_ID, "id")
        }
    }
}