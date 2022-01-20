package net.corda.httprpc.security.read.rbac

import net.corda.httprpc.exception.NotAuthenticatedException
import net.corda.httprpc.security.read.Password
import net.corda.libs.permission.PermissionValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RBACSecurityManagerTest {

    private val validator = mock<PermissionValidator>()
    private val manager = RBACSecurityManager(validator)

    @Test
    fun `unauthenticated user throws RPCSecurityException`() {
        val passwordCapture = argumentCaptor<CharArray>()

        whenever(validator.authenticateUser(eq("principal1"), passwordCapture.capture())).thenReturn(false)

        val e = assertThrows<NotAuthenticatedException> {
            manager.authenticate("principal1", Password("pass1"))
        }

        assertEquals("User not authenticated.", e.message)
    }

    @Test
    fun `authenticate user utilizes permission validator to authenticate and then builds subject`() {
        val passwordCapture = argumentCaptor<CharArray>()

        whenever(validator.authenticateUser(eq("principal1"), passwordCapture.capture())).thenReturn(true)

        val subject = manager.authenticate("principal1", Password("pass1"))

        assertEquals("principal1", subject.principal)
        assertEquals(1, passwordCapture.allValues.size)
        assertEquals("pass1", String(passwordCapture.firstValue))
    }
}