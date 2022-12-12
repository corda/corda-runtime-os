package net.corda.membership.impl.httprpc.v1

import net.corda.httprpc.exception.BadRequestException
import net.corda.httprpc.exception.InvalidInputDataException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.exception.ServiceUnavailableException
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.client.CouldNotFindMemberException
import net.corda.membership.client.MGMOpsClient
import net.corda.membership.client.MemberNotAnMgmException
import net.corda.virtualnode.ShortHash
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MGMRpcOpsTest {
    companion object {
        private const val HOLDING_IDENTITY_ID = "111213141500"
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

    private val mgmGenerateGroupPolicyResponseDto = "grouppolicy"

    private val mgmOpsClient: MGMOpsClient = mock {
        on { generateGroupPolicy(any()) } doReturn mgmGenerateGroupPolicyResponseDto
    }

    private val mgmRpcOps = MGMRpcOpsImpl(
        lifecycleCoordinatorFactory,
        mgmOpsClient
    )

    @Test
    fun `starting and stopping the service succeeds`() {
        mgmRpcOps.start()
        assertTrue(mgmRpcOps.isRunning)
        mgmRpcOps.stop()
        assertFalse(mgmRpcOps.isRunning)
    }

    @Test
    fun `generateGroupPolicy calls the client svc`() {
        mgmRpcOps.start()
        mgmRpcOps.activate("")
        mgmRpcOps.generateGroupPolicy(HOLDING_IDENTITY_ID)
        verify(mgmOpsClient)
            .generateGroupPolicy(eq((ShortHash.of(HOLDING_IDENTITY_ID))))
        mgmRpcOps.deactivate("")
        mgmRpcOps.stop()
    }

    @Test
    fun `generateGroupPolicy throws resource not found for invalid member`() {
        mgmRpcOps.start()
        mgmRpcOps.activate("")
        whenever(mgmOpsClient.generateGroupPolicy(any())).doThrow(mock<CouldNotFindMemberException>())

        assertThrows<ResourceNotFoundException> {
            mgmRpcOps.generateGroupPolicy(HOLDING_IDENTITY_ID)
        }
    }

    @Test
    fun `generateGroupPolicy throws invalid input for non MGM meber`() {
        mgmRpcOps.start()
        mgmRpcOps.activate("")
        whenever(mgmOpsClient.generateGroupPolicy(any())).doThrow(mock<MemberNotAnMgmException>())

        assertThrows<InvalidInputDataException> {
            mgmRpcOps.generateGroupPolicy(HOLDING_IDENTITY_ID)
        }
    }

    @Test
    fun `generateGroupPolicy throws bad request if short hash is invalid`() {
        mgmRpcOps.start()
        mgmRpcOps.activate("")

        assertThrows<BadRequestException> {
            mgmRpcOps.generateGroupPolicy("ABS09234745D")
        }
    }

    @Test
    fun `operation fails when svc is not running`() {
        val ex = assertFailsWith<ServiceUnavailableException> {
            mgmRpcOps.generateGroupPolicy(HOLDING_IDENTITY_ID)
        }
        assertEquals("MGMRpcOpsImpl is not running. Operation cannot be fulfilled.", ex.message)
    }
}
