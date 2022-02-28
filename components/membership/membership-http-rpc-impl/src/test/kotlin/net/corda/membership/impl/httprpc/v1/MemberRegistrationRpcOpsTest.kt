package net.corda.membership.impl.httprpc.v1

import net.corda.httprpc.exception.ServiceUnavailableException
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.httprpc.MemberOpsClient
import net.corda.membership.httprpc.types.MemberRegistrationRequest
import net.corda.membership.httprpc.types.RegistrationAction
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MemberRegistrationRpcOpsTest {
    companion object {
        private const val VIRTUAL_NODE_ID = "DUMMY_ID"
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

    private val memberOpsClient: MemberOpsClient = mock()

    private val memberRegistrationRpcOps = MemberRegistrationRpcOpsImpl(
        lifecycleCoordinatorFactory,
        memberOpsClient
    )

    private val registrationRequest = MemberRegistrationRequest(
        VIRTUAL_NODE_ID,
        RegistrationAction.REQUEST_JOIN
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
        memberRegistrationRpcOps.startRegistration(registrationRequest)
        verify(memberOpsClient).startRegistration(eq(registrationRequest))
        memberRegistrationRpcOps.stop()
    }

    @Test
    fun `checking registration progress calls the client svc`() {
        memberRegistrationRpcOps.start()
        memberRegistrationRpcOps.checkRegistrationProgress(VIRTUAL_NODE_ID)
        verify(memberOpsClient).checkRegistrationProgress(eq(VIRTUAL_NODE_ID))
        memberRegistrationRpcOps.stop()
    }

    @Test
    fun `operation fails when svc is not running`() {
        val ex = assertFailsWith<ServiceUnavailableException> {
            memberRegistrationRpcOps.checkRegistrationProgress(VIRTUAL_NODE_ID)
        }
        assertEquals("MemberRegistrationRpcOpsImpl is not running. Operation cannot be fulfilled.", ex.message)
    }
}