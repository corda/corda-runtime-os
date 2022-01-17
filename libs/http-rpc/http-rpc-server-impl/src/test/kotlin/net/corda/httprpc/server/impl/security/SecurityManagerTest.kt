package net.corda.httprpc.server.impl.security

import net.corda.httprpc.rpc.proxies.RpcAuthHelper
import net.corda.httprpc.security.AuthorizingSubject
import net.corda.httprpc.server.impl.security.provider.AuthenticationProvider
import net.corda.httprpc.server.impl.security.provider.credentials.tokens.BearerTokenAuthenticationCredentials
import net.corda.httprpc.server.impl.security.provider.credentials.tokens.UsernamePasswordAuthenticationCredentials
import net.corda.httprpc.server.impl.security.provider.scheme.AuthenticationSchemeProvider
import net.corda.httprpc.RpcOps
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.security.auth.login.FailedLoginException
import net.corda.httprpc.security.read.RPCSecurityException
import org.junit.jupiter.api.Assertions.assertEquals

interface TestRpcOps : RpcOps {
  fun dummy()
  fun dummy2()
}

class TestRpcOpsImpl : TestRpcOps {
  override val protocolVersion: Int
    get() = 2

  override fun dummy() {}
  override fun dummy2() {}
}

class SecurityManagerTest {
    private val authenticationProvider1: AuthenticationProvider = mock()
    private val authenticationProvider2: AuthenticationProvider = mock(extraInterfaces = arrayOf(AuthenticationSchemeProvider::class))
    private val subject: AuthorizingSubject = mock()
    private val password = "password"
    private val userAlice = User("Alice", password, setOf("ALL"))
    private val userBob = User("Bob", password, setOf("InvokeRpc:net.corda.httprpc.server.security.TestRPCOps#dummy2"))
    private val securityManager = SecurityManagerRPCImpl(setOf(authenticationProvider1, authenticationProvider2))

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
    fun `authenticate providers last throws RPCSecurityException that gets rethrown as FailedLoginException`() {
        whenever(authenticationProvider2.supports(any<UsernamePasswordAuthenticationCredentials>())).thenReturn(true)

        whenever(authenticationProvider1.authenticate(any())).thenAnswer { throw RPCSecurityException("failed to login1") }
        whenever(authenticationProvider2.authenticate(any())).thenAnswer { throw RPCSecurityException("failed to login2") }

        val e = assertThrows(FailedLoginException::class.java) {
            securityManager.authenticate(UsernamePasswordAuthenticationCredentials("guest", password))
        }
        assertEquals("failed to login2", e.message)
    }

    @Test
    fun `authenticate provider throws RPCSecurityException that gets rethrown as FailedLoginException`() {
        whenever(authenticationProvider1.authenticate(any())).thenAnswer { throw RPCSecurityException("failed to login1") }

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
        whenever(subject.isPermitted(RpcAuthHelper.methodFullName(TestRpcOps::class.java.getMethod("dummy2")))).thenReturn(true)

        val authenticatedBob = securityManager.authenticate(UsernamePasswordAuthenticationCredentials(userBob.username, password))

        val isBobAuthorizedToMethod = securityManager.authorize(authenticatedBob, TestRpcOps::class.java.getMethod("dummy"))
        val isBobAuthorizedToMethod2 = securityManager.authorize(authenticatedBob, TestRpcOps::class.java.getMethod("dummy2"))

        assert(isBobAuthorizedToMethod2)
        assertFalse(isBobAuthorizedToMethod)
    }

    @Test
    fun `isPermitted_authenticatedUserAndPermittedAll_shouldBeAuthorizedToEveryMethod`() {
        whenever(subject.isPermitted(RpcAuthHelper.methodFullName(TestRpcOps::class.java.getMethod("dummy")))).thenReturn(true)
        whenever(subject.isPermitted(RpcAuthHelper.methodFullName(TestRpcOps::class.java.getMethod("dummy2")))).thenReturn(true)

        val authenticatedAlice = securityManager.authenticate(UsernamePasswordAuthenticationCredentials(userAlice.username, password))

        val isAliceAuthorizedToMethod = securityManager.authorize(authenticatedAlice, TestRpcOps::class.java.getMethod("dummy"))
        val isAliceAuthorizedToMethod2 = securityManager.authorize(authenticatedAlice, TestRpcOps::class.java.getMethod("dummy2"))

        assert(isAliceAuthorizedToMethod)
        assert(isAliceAuthorizedToMethod2)
    }

    @Test
    fun `getSchemes_shouldReturnSchemeWithParameters`() {
        assertArrayEquals(arrayOf(authenticationProvider2), securityManager.getSchemeProviders().toTypedArray())
    }

  private data class User(val username: String, val password: String, val permissions: Set<String>)
}
