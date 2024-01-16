package net.corda.membership.impl.rest.v1

import net.corda.crypto.core.ShortHash
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.client.CouldNotFindEntityException
import net.corda.membership.client.Entity
import net.corda.membership.client.MGMResourceClient
import net.corda.membership.client.MemberNotAnMgmException
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.exception.ServiceUnavailableException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID
import kotlin.test.assertFailsWith

class MGMAdminRestResourceTest {
    private companion object {
        const val HOLDING_IDENTITY_ID = "111213141500"
        const val INVALID_SHORT_HASH = "ABS09234745D"
        const val REQUEST_ID = "b305129b-8c92-4092-b3a2-e6d452ce2b01"

        fun String.shortHash() = ShortHash.of(this)
        fun String.uuid(): UUID = UUID.fromString(this)
    }

    private var coordinatorIsRunning = false
    private val coordinator = mock<LifecycleCoordinator> {
        on { isRunning } doAnswer { coordinatorIsRunning }
        on { start() } doAnswer { coordinatorIsRunning = true }
        on { stop() } doAnswer { coordinatorIsRunning = false }
    }

    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }

    private val mgmResourceClient = mock<MGMResourceClient>()

    private val mgmAdminRestResource = MGMAdminRestResourceImpl(
        lifecycleCoordinatorFactory,
        mgmResourceClient,
        mock(),
    )

    private fun startService() {
        mgmAdminRestResource.start()
        mgmAdminRestResource.activate("")
    }

    private fun stopService() {
        mgmAdminRestResource.deactivate("")
        mgmAdminRestResource.stop()
    }

    @Test
    fun `starting and stopping the service succeeds`() {
        mgmAdminRestResource.start()
        assertTrue(mgmAdminRestResource.isRunning)
        mgmAdminRestResource.stop()
        assertFalse(mgmAdminRestResource.isRunning)
    }

    @Test
    fun `operation fails when svc is not running`() {
        val ex = assertFailsWith<ServiceUnavailableException> {
            mgmAdminRestResource.forceDeclineRegistrationRequest(HOLDING_IDENTITY_ID, REQUEST_ID)
        }
        assertEquals("MGMAdminRestResourceImpl is not running. Operation cannot be fulfilled.", ex.message)
    }

    @Nested
    inner class ForceDeclineRegistrationRequestTests {
        @BeforeEach
        fun setUp() = startService()

        @AfterEach
        fun tearDown() = stopService()

        @Test
        fun `forceDeclineRegistrationRequest delegates correctly to MGM resource client`() {
            mgmAdminRestResource.forceDeclineRegistrationRequest(HOLDING_IDENTITY_ID, REQUEST_ID)

            verify(mgmResourceClient).forceDeclineRegistrationRequest(
                HOLDING_IDENTITY_ID.shortHash(),
                REQUEST_ID.uuid()
            )
        }

        @Test
        fun `forceDeclineRegistrationRequest throws resource not found for invalid member`() {
            val couldNotFindEntityException = mock<CouldNotFindEntityException> {
                on { entity } doReturn Entity.MEMBER
            }
            whenever(
                mgmResourceClient.forceDeclineRegistrationRequest(
                    HOLDING_IDENTITY_ID.shortHash(),
                    REQUEST_ID.uuid()
                )
            ).doThrow(couldNotFindEntityException)

            assertThrows<ResourceNotFoundException> {
                mgmAdminRestResource.forceDeclineRegistrationRequest(HOLDING_IDENTITY_ID, REQUEST_ID)
            }
        }

        @Test
        fun `forceDeclineRegistrationRequest throws invalid input for non MGM member`() {
            whenever(
                mgmResourceClient.forceDeclineRegistrationRequest(
                    HOLDING_IDENTITY_ID.shortHash(),
                    REQUEST_ID.uuid()
                )
            ).doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                mgmAdminRestResource.forceDeclineRegistrationRequest(HOLDING_IDENTITY_ID, REQUEST_ID)
            }
        }

        @Test
        fun `forceDeclineRegistrationRequest throws bad request if short hash is invalid`() {
            assertThrows<BadRequestException> {
                mgmAdminRestResource.forceDeclineRegistrationRequest(INVALID_SHORT_HASH, REQUEST_ID)
            }
        }

        @Test
        fun `forceDeclineRegistrationRequest throws bad request if request is not found or already completed`() {
            whenever(
                mgmResourceClient.forceDeclineRegistrationRequest(
                    HOLDING_IDENTITY_ID.shortHash(),
                    REQUEST_ID.uuid()
                )
            ).doThrow(mock<IllegalArgumentException>())

            assertThrows<BadRequestException> {
                mgmAdminRestResource.forceDeclineRegistrationRequest(HOLDING_IDENTITY_ID, REQUEST_ID)
            }
        }
    }
}
