package net.corda.httprpc.server.impl.security

import io.javalin.http.ForbiddenResponse
import net.corda.httprpc.security.AuthorizingSubject
import net.corda.httprpc.server.impl.security.provider.AuthenticationProvider
import net.corda.httprpc.server.impl.security.provider.credentials.tokens.BearerTokenAuthenticationCredentials
import net.corda.httprpc.server.impl.security.provider.credentials.tokens.UsernamePasswordAuthenticationCredentials
import net.corda.httprpc.server.impl.security.provider.scheme.AuthenticationSchemeProvider
import net.corda.httprpc.RpcOps
import net.corda.httprpc.server.impl.context.ContextUtils
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.security.auth.login.FailedLoginException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertDoesNotThrow
import java.lang.reflect.Method

class HttpRpcSecurityManagerTest {
    private val authenticationProvider1: AuthenticationProvider = mock()
    private val authenticationProvider2: AuthenticationProvider = mock(extraInterfaces = arrayOf(AuthenticationSchemeProvider::class))
    private val subject: AuthorizingSubject = mock()
    private val password = "password"
    private val userAlice = User("Alice", password, setOf("ALL"))
    private val userBob = User("Bob", password, setOf("InvokeRpc:net.corda.httprpc.server.security.TestRPCOps#dummy2"))
    private val securityManager = SecurityManagerRPCImpl(setOf(authenticationProvider1, authenticationProvider2))

    private companion object {

        private fun authorize(authenticatedUser: AuthorizingSubject, method: Method) {
            return ContextUtils.authorize(authenticatedUser, methodFullName(method))
        }

        private fun methodFullName(method: Method): String = methodFullName(method.declaringClass, method.name)

        private fun methodFullName(clazz: Class<*>, methodName: String): String {
            require(clazz.isInterface) { "Must be an interface: $clazz" }
            require(RpcOps::class.java.isAssignableFrom(clazz)) { "Must be assignable from RPCOps: $clazz" }
            return clazz.name + "#" + methodName
        }
    }

    @BeforeEach
    fun setUp() {
        whenever(authenticationProvider1.supports(any<UsernamePasswordAuthenticationCredentials>())).thenReturn(true)
        whenever(authenticationProvider2.supports(any<BearerTokenAuthenticationCredentials>())).thenReturn(true)

        whenever(authenticationProvider1.authenticate(UsernamePasswordAuthenticationCredentials(userAlice.username, password))).thenReturn(
            subject
        )
    }

    @Test
    fun `authenticate_validUser_shouldReturnAuthenticatedUser`() {
        val auth = securityManager.authenticate(UsernamePasswordAuthenticationCredentials(userAlice.username, password))
        assertSame(subject, auth)
    }

    @Test
    fun `authenticate_validUserForSecondProvider_shouldReturnAuthenticatedUser`() {
        whenever(authenticationProvider1.authenticate(any())).thenAnswer { throw FailedLoginException() }
        whenever(authenticationProvider2.authenticate(any<BearerTokenAuthenticationCredentials>())).thenReturn(subject)

        val auth = securityManager.authenticate(BearerTokenAuthenticationCredentials("testToken"))
        assertSame(subject, auth)
    }

    @Test
    fun `authenticate_notSupportedCredential_shouldThrowFailedLoginException`() {
        whenever(authenticationProvider2.supports(any<BearerTokenAuthenticationCredentials>())).thenReturn(false)

        assertThrows(FailedLoginException::class.java) {
            securityManager.authenticate(BearerTokenAuthenticationCredentials("testToken"))
        }
    }

    @Test
    fun `authenticate providers last throws FailedLoginException that gets rethrown as FailedLoginException`() {
        whenever(authenticationProvider2.supports(any<UsernamePasswordAuthenticationCredentials>())).thenReturn(true)

        whenever(authenticationProvider1.authenticate(any())).thenAnswer { throw FailedLoginException("failed to login1") }
        whenever(authenticationProvider2.authenticate(any())).thenAnswer { throw FailedLoginException("failed to login2") }

        val e = assertThrows(FailedLoginException::class.java) {
            securityManager.authenticate(UsernamePasswordAuthenticationCredentials("guest", password))
        }
        assertEquals("failed to login2", e.message)
    }

    @Test
    fun `authenticate provider throws FailedLoginException that gets rethrown as FailedLoginException`() {
        whenever(authenticationProvider1.authenticate(any())).thenAnswer { throw FailedLoginException("failed to login1") }

        val e = assertThrows(FailedLoginException::class.java) {
            securityManager.authenticate(UsernamePasswordAuthenticationCredentials("guest", password))
        }
        assertEquals("failed to login1", e.message)
    }

    @Test
    fun `authenticate_invalidUser_shouldThrowFailedLoginException`() {
        whenever(authenticationProvider1.authenticate(any())).thenAnswer { throw FailedLoginException() }
        whenever(authenticationProvider2.authenticate(any())).thenAnswer { throw FailedLoginException() }

        assertThrows(FailedLoginException::class.java) {
            securityManager.authenticate(UsernamePasswordAuthenticationCredentials("guest", password))
        }
    }

    @Test
    fun `isPermitted_authenticatedUserAndPermittedToMethod_shouldBeAuthorizedToOnlyThat`() {
        whenever(authenticationProvider1.authenticate(UsernamePasswordAuthenticationCredentials(userBob.username, password))).thenReturn(
            subject
        )
        whenever(subject.isPermitted(methodFullName(TestRpcOps::class.java.getMethod("dummy2")))).thenReturn(true)

        val authenticatedBob = securityManager.authenticate(UsernamePasswordAuthenticationCredentials(userBob.username, password))

        assertThrows(ForbiddenResponse::class.java) {
            authorize(authenticatedBob, TestRpcOps::class.java.getMethod("dummy"))
        }

        assertDoesNotThrow {
            authorize(authenticatedBob, TestRpcOps::class.java.getMethod("dummy2"))
        }
    }

    @Test
    fun `isPermitted_authenticatedUserAndPermittedAll_shouldBeAuthorizedToEveryMethod`() {
        whenever(subject.isPermitted(methodFullName(TestRpcOps::class.java.getMethod("dummy")))).thenReturn(true)
        whenever(subject.isPermitted(methodFullName(TestRpcOps::class.java.getMethod("dummy2")))).thenReturn(true)

        val authenticatedAlice = securityManager.authenticate(UsernamePasswordAuthenticationCredentials(userAlice.username, password))

        assertDoesNotThrow {
            authorize(authenticatedAlice, TestRpcOps::class.java.getMethod("dummy"))
        }
        assertDoesNotThrow {
            authorize(authenticatedAlice, TestRpcOps::class.java.getMethod("dummy2"))
        }
    }

    @Test
    fun `getSchemes_shouldReturnSchemeWithParameters`() {
        assertArrayEquals(arrayOf(authenticationProvider2), securityManager.getSchemeProviders().toTypedArray())
    }

    private data class User(val username: String, val password: String, val permissions: Set<String>)
}
