package net.corda.membership.impl.httprpc.v1

import net.corda.httprpc.exception.BadRequestException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.exception.ServiceUnavailableException
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.client.MemberOpsClient
import net.corda.membership.client.dto.MemberInfoSubmittedDto
import net.corda.membership.client.dto.RegistrationRequestProgressDto
import net.corda.membership.client.dto.RegistrationRequestStatusDto
import net.corda.membership.client.dto.RegistrationStatusDto
import net.corda.membership.client.dto.SubmittedRegistrationStatus
import net.corda.membership.httprpc.v1.types.request.MemberRegistrationRequest
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
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.whenever
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
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getByHoldingIdentityShortHash(holdingIdShortHash) } doReturn mock()
    }

    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }

    private val registrationProgress = RegistrationRequestProgressDto(
        "RequestId",
        clock.instant(),
        SubmittedRegistrationStatus.SUBMITTED,
        "",
        MemberInfoSubmittedDto(emptyMap())
    )

    private val memberOpsClient: MemberOpsClient = mock {
        on { startRegistration(any()) } doReturn registrationProgress
    }

    private val memberRegistrationRpcOps = MemberRegistrationRestResourceImpl(
        lifecycleCoordinatorFactory,
        memberOpsClient,
        virtualNodeInfoReadService,
    )

    private val registrationRequest = MemberRegistrationRequest(
        ACTION,
        context = mock()
    )

    @Test
    fun `starting and stopping the service succeeds`() {
        memberRegistrationRpcOps.start()
        assertTrue(memberRegistrationRpcOps.isRunning)
        memberRegistrationRpcOps.stop()
        assertFalse(memberRegistrationRpcOps.isRunning)
    }

    @Test
    fun `starting registration calls the client svc`() {
        memberRegistrationRpcOps.start()
        memberRegistrationRpcOps.activate("")
        memberRegistrationRpcOps.startRegistration(HOLDING_IDENTITY_ID, registrationRequest)
        verify(memberOpsClient).startRegistration(eq(registrationRequest.toDto(HOLDING_IDENTITY_ID)))
        memberRegistrationRpcOps.deactivate("")
        memberRegistrationRpcOps.stop()
    }

    @Test
    fun `starting registration with invalid member will fail`() {
        memberRegistrationRpcOps.start()
        memberRegistrationRpcOps.activate("")

        assertThrows<ResourceNotFoundException> {
            memberRegistrationRpcOps.startRegistration("123456789000", registrationRequest)
        }
    }

    @Test
    fun `startRegistration fails when service is not running`() {
        val ex = assertFailsWith<ServiceUnavailableException> {
            memberRegistrationRpcOps.startRegistration(HOLDING_IDENTITY_ID, registrationRequest)
        }
        assertThat(ex).hasMessage("MemberRegistrationRestResourceImpl is not running. Operation cannot be fulfilled.")
    }

    @Test
    fun `startRegistration throws bad request if short hash is invalid`() {
        memberRegistrationRpcOps.start()
        memberRegistrationRpcOps.activate("")

        assertFailsWith<BadRequestException> {
            memberRegistrationRpcOps.startRegistration(INVALID_HOLDING_IDENTITY_ID, registrationRequest)
        }

        memberRegistrationRpcOps.deactivate("")
        memberRegistrationRpcOps.stop()
    }

    @Test
    fun `checkRegistrationProgress fails when service is not running`() {
        val ex = assertFailsWith<ServiceUnavailableException> {
            memberRegistrationRpcOps.checkRegistrationProgress(HOLDING_IDENTITY_ID)
        }
        assertThat(ex).hasMessage("MemberRegistrationRestResourceImpl is not running. Operation cannot be fulfilled.")
    }
    @Test
    fun `checkSpecificRegistrationProgress fails when service is not running`() {
        val ex = assertFailsWith<ServiceUnavailableException> {
            memberRegistrationRpcOps.checkSpecificRegistrationProgress(HOLDING_IDENTITY_ID, "id")
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
        whenever(memberOpsClient.checkRegistrationProgress(holdingIdShortHash)).doReturn(data)
        memberRegistrationRpcOps.start()
        memberRegistrationRpcOps.activate("")

        val status = memberRegistrationRpcOps.checkRegistrationProgress(HOLDING_IDENTITY_ID)

        assertThat(status)
            .hasSize(data.size)
            .containsAnyElementsOf(data.map { it.fromDto() })
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

        whenever(memberOpsClient.checkSpecificRegistrationProgress(holdingIdShortHash, "id")).doReturn(data)
        memberRegistrationRpcOps.start()
        memberRegistrationRpcOps.activate("")

        val status = memberRegistrationRpcOps.checkSpecificRegistrationProgress(HOLDING_IDENTITY_ID, "id")

        assertThat(status)
            .isEqualTo(data.fromDto())
    }

    @Test
    fun `checkSpecificRegistrationProgress returns null when no data is returned`() {
        whenever(memberOpsClient.checkSpecificRegistrationProgress(holdingIdShortHash, "id")).doReturn(null)
        memberRegistrationRpcOps.start()
        memberRegistrationRpcOps.activate("")

        val status = memberRegistrationRpcOps.checkSpecificRegistrationProgress(HOLDING_IDENTITY_ID, "id")

        assertThat(status)
            .isNull()
    }

    @Test
    fun `checkSpecificRegistrationProgress throws bad request if short hash is invalid`() {
        memberRegistrationRpcOps.start()
        memberRegistrationRpcOps.activate("")

        assertFailsWith<BadRequestException> {
            memberRegistrationRpcOps.checkSpecificRegistrationProgress(INVALID_HOLDING_IDENTITY_ID, "id")
        }
    }
}