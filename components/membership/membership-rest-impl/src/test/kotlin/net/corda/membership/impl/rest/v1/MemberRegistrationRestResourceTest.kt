package net.corda.membership.impl.rest.v1

import net.corda.crypto.core.ShortHash
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.client.CouldNotFindEntityException
import net.corda.membership.client.Entity
import net.corda.membership.client.MemberResourceClient
import net.corda.membership.client.RegistrationProgressNotFoundException
import net.corda.membership.client.ServiceNotReadyException
import net.corda.membership.client.dto.MemberInfoSubmittedDto
import net.corda.membership.client.dto.RegistrationRequestProgressDto
import net.corda.membership.client.dto.RegistrationRequestStatusDto
import net.corda.membership.client.dto.RegistrationStatusDto
import net.corda.membership.client.dto.SubmittedRegistrationStatus
import net.corda.membership.lib.ContextDeserializationException
import net.corda.membership.rest.v1.types.request.MemberRegistrationRequest
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.test.util.time.TestClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.Exception
import java.time.Instant
import kotlin.test.assertFailsWith

class MemberRegistrationRestResourceTest {
    private companion object {
        const val HOLDING_IDENTITY_ID = "1234567890ab"
        const val INVALID_HOLDING_IDENTITY_ID = "${HOLDING_IDENTITY_ID}00"
        val holdingIdShortHash = ShortHash.of(HOLDING_IDENTITY_ID)
        val clock = TestClock(Instant.ofEpochSecond(100))
        const val SERIAL = 1L
        const val REQUEST_ID = "id"
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
        on { startRegistration(any(), any()) } doReturn registrationProgress
    }

    private val memberRegistrationRestResource = MemberRegistrationRestResourceImpl(
        lifecycleCoordinatorFactory,
        memberResourceClient,
        mock(),
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
        memberRegistrationRestResource.startRegistration(HOLDING_IDENTITY_ID, MemberRegistrationRequest(emptyMap()))
        verify(memberResourceClient).startRegistration(ShortHash.of(HOLDING_IDENTITY_ID), emptyMap())
        memberRegistrationRestResource.deactivate("")
        memberRegistrationRestResource.stop()
    }

    @Test
    fun `starting registration with invalid member will fail`() {
        memberRegistrationRestResource.start()
        memberRegistrationRestResource.activate("")
        whenever(memberResourceClient.startRegistration(any(), any()))
            .doThrow(CouldNotFindEntityException(Entity.VIRTUAL_NODE, holdingIdShortHash))

        assertThrows<ResourceNotFoundException> {
            memberRegistrationRestResource.startRegistration(HOLDING_IDENTITY_ID, MemberRegistrationRequest(emptyMap()))
        }
    }

    @Test
    fun `startRegistration fails when service is not running`() {
        val ex = assertFailsWith<ServiceUnavailableException> {
            memberRegistrationRestResource.startRegistration(HOLDING_IDENTITY_ID, MemberRegistrationRequest(emptyMap()))
        }
        assertThat(ex).hasMessage("MemberRegistrationRestResourceImpl is not running. Operation cannot be fulfilled.")
    }

    @Test
    fun `startRegistration throws bad request if short hash is invalid`() {
        memberRegistrationRestResource.start()
        memberRegistrationRestResource.activate("")

        assertFailsWith<BadRequestException> {
            memberRegistrationRestResource.startRegistration(INVALID_HOLDING_IDENTITY_ID, MemberRegistrationRequest(emptyMap()))
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
            memberRegistrationRestResource.checkSpecificRegistrationProgress(HOLDING_IDENTITY_ID, REQUEST_ID)
        }
        assertThat(ex).hasMessage("MemberRegistrationRestResourceImpl is not running. Operation cannot be fulfilled.")
    }

    @Test
    fun `checkRegistrationProgress returns the correct data`() {
        val data = (1..3).map {
            RegistrationRequestStatusDto(
                registrationId = "id $it",
                registrationSent = Instant.ofEpochSecond(20L + it),
                registrationUpdated = Instant.ofEpochSecond(200L + it),
                registrationStatus = RegistrationStatusDto.APPROVED,
                memberInfoSubmitted = MemberInfoSubmittedDto(mapOf("key $it" to "value")),
                serial = SERIAL,
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
        ).doThrow(CouldNotFindEntityException(Entity.VIRTUAL_NODE, holdingIdShortHash))
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
    fun `checkRegistrationProgress throws 500 when deserialization fails`() {
        whenever(memberResourceClient.checkRegistrationProgress(holdingIdShortHash)).doThrow(
            ContextDeserializationException
        )
        memberRegistrationRestResource.start()
        memberRegistrationRestResource.activate("")

        assertThrows<InternalServerException> {
            memberRegistrationRestResource.checkRegistrationProgress(HOLDING_IDENTITY_ID)
        }
    }

    @Test
    fun `checkSpecificRegistrationProgress returns the correct data`() {
        val data = RegistrationRequestStatusDto(
            registrationId = REQUEST_ID,
            registrationSent = Instant.ofEpochSecond(20L),
            registrationUpdated = Instant.ofEpochSecond(200L),
            registrationStatus = RegistrationStatusDto.APPROVED,
            memberInfoSubmitted = MemberInfoSubmittedDto(mapOf("key" to "value")),
            serial = SERIAL,
        )

        whenever(memberResourceClient.checkSpecificRegistrationProgress(holdingIdShortHash, REQUEST_ID)).doReturn(data)
        memberRegistrationRestResource.start()
        memberRegistrationRestResource.activate("")

        val status = memberRegistrationRestResource.checkSpecificRegistrationProgress(HOLDING_IDENTITY_ID, REQUEST_ID)

        assertThat(status)
            .isEqualTo(data.fromDto())
    }

    @Test
    fun `checkSpecificRegistrationProgress throw 404 when the member can not be found`() {
        whenever(memberResourceClient.checkSpecificRegistrationProgress(holdingIdShortHash, REQUEST_ID))
            .doThrow(CouldNotFindEntityException(Entity.VIRTUAL_NODE, holdingIdShortHash))
        memberRegistrationRestResource.start()
        memberRegistrationRestResource.activate("")

        assertThrows<ResourceNotFoundException> {
            memberRegistrationRestResource.checkSpecificRegistrationProgress(HOLDING_IDENTITY_ID, REQUEST_ID)
        }
    }

    @Test
    fun `checkSpecificRegistrationProgress throw 404 when the request can not be found`() {
        whenever(memberResourceClient.checkSpecificRegistrationProgress(holdingIdShortHash, REQUEST_ID))
            .doThrow(RegistrationProgressNotFoundException(""))
        memberRegistrationRestResource.start()
        memberRegistrationRestResource.activate("")

        assertThrows<ResourceNotFoundException> {
            memberRegistrationRestResource.checkSpecificRegistrationProgress(HOLDING_IDENTITY_ID, REQUEST_ID)
        }
    }

    @Test
    fun `checkSpecificRegistrationProgress throw 503 when the db is not ready`() {
        whenever(memberResourceClient.checkSpecificRegistrationProgress(holdingIdShortHash, REQUEST_ID))
            .doThrow(ServiceNotReadyException(Exception("")))
        memberRegistrationRestResource.start()
        memberRegistrationRestResource.activate("")

        assertThrows<ServiceUnavailableException> {
            memberRegistrationRestResource.checkSpecificRegistrationProgress(HOLDING_IDENTITY_ID, REQUEST_ID)
        }
    }

    @Test
    fun `checkSpecificRegistrationProgress throws bad request if short hash is invalid`() {
        memberRegistrationRestResource.start()
        memberRegistrationRestResource.activate("")

        assertFailsWith<BadRequestException> {
            memberRegistrationRestResource.checkSpecificRegistrationProgress(INVALID_HOLDING_IDENTITY_ID, REQUEST_ID)
        }
    }

    @Test
    fun `checkSpecificRegistrationProgress throws 500 when deserialization fails`() {
        whenever(memberResourceClient.checkSpecificRegistrationProgress(holdingIdShortHash, REQUEST_ID)).doThrow(
            ContextDeserializationException
        )
        memberRegistrationRestResource.start()
        memberRegistrationRestResource.activate("")

        assertThrows<InternalServerException> {
            memberRegistrationRestResource.checkSpecificRegistrationProgress(HOLDING_IDENTITY_ID, REQUEST_ID)
        }
    }
}
