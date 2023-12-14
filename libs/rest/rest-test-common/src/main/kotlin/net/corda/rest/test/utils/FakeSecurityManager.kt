package net.corda.rest.test.utils

import net.corda.rest.security.AuthServiceId
import net.corda.rest.security.AuthorizingSubject
import net.corda.rest.security.read.Password
import net.corda.rest.security.read.RestSecurityManager
import javax.security.auth.login.FailedLoginException

class FakeSecurityManager : RestSecurityManager {

    companion object {
        const val USERNAME = "admin"
        const val PASSWORD = "admin"
        data class SecurityCheck(val action: String, val arguments: List<String>)
    }

    private var _isRunning = false

    override val isRunning: Boolean
        get() = _isRunning

    override fun start() {
        _isRunning = true
    }

    override fun stop() {
        _isRunning = false
    }

    override val id = AuthServiceId("FakeSecurityManager")

    private val _checksExecuted: MutableList<SecurityCheck> = mutableListOf()

    val checksExecuted: List<SecurityCheck> = _checksExecuted

    private inner class RecordKeepingSubject(override val principal: String) : AuthorizingSubject {
        override fun isPermitted(action: String, vararg arguments: String): Boolean {
            _checksExecuted.add(SecurityCheck(action, arguments.asList()))
            return true
        }
    }

    override fun authenticate(principal: String, password: Password): AuthorizingSubject {
        return if (USERNAME.equals(principal, true) && password == Password(PASSWORD)) {
            RecordKeepingSubject(principal)
        } else {
            throw FailedLoginException("No provisions for: $principal")
        }
    }

    override fun buildSubject(principal: String): AuthorizingSubject {
        return RecordKeepingSubject(FakeSecurityManager::class.java.simpleName)
    }

    fun forgetChecks() {
        _checksExecuted.clear()
    }
}
