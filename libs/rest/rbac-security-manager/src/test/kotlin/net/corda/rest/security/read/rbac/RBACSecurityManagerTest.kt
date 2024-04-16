package net.corda.rest.security.read.rbac

import net.corda.libs.permission.PermissionValidator
import net.corda.libs.permissions.manager.AuthenticationState
import net.corda.libs.permissions.manager.BasicAuthenticationService
import net.corda.libs.permissions.manager.ExpiryStatus
import net.corda.rest.security.read.Password
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.security.auth.login.FailedLoginException

class RBACSecurityManagerTest {

    private val validator = mock<PermissionValidator>()
    private val basicAuthenticationService = mock<BasicAuthenticationService>()
    private val manager = RBACSecurityManager({ validator }, basicAuthenticationService)

    @Test
    fun `unauthenticated user throws FailedLoginException`() {
        val passwordCapture = argumentCaptor<CharArray>()

        whenever(basicAuthenticationService.authenticateUser(eq("principal1"), passwordCapture.capture())).thenReturn(
            AuthenticationState(false, null)
        )

        val e = assertThrows<FailedLoginException> {
            manager.authenticate("principal1", Password("pass1"))
        }

        assertEquals("User not authenticated.", e.message)
    }

    @Test
    fun `authenticate user utilizes permission validator to authenticate and then builds subject`() {
        val passwordCapture = argumentCaptor<CharArray>()

        whenever(basicAuthenticationService.authenticateUser(eq("principal1"), passwordCapture.capture())).thenReturn(
            AuthenticationState(true, ExpiryStatus.ACTIVE)
        )

        val subject = manager.authenticate("principal1", Password("pass1"))

        assertEquals("principal1", subject.principal)
        assertEquals(1, passwordCapture.allValues.size)
        assertEquals("pass1", String(passwordCapture.firstValue))
    }
}
