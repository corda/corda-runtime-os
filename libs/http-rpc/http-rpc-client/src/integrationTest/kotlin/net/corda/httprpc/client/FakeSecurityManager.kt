package net.corda.httprpc.client

import net.corda.httprpc.exception.NotAuthenticatedException
import net.corda.httprpc.security.AuthServiceId
import net.corda.httprpc.security.AuthorizingSubject
import net.corda.httprpc.security.read.AdminSubject
import net.corda.httprpc.security.read.Password
import net.corda.httprpc.security.read.RPCSecurityManager

class FakeSecurityManager : RPCSecurityManager {

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

    override fun authenticate(principal: String, password: Password): AuthorizingSubject {
        return "admin".let {
            if (it == principal && password == Password(it)) {
                AdminSubject(principal)
            } else {
                throw NotAuthenticatedException("No provisions for: $principal")
            }
        }
    }

    override fun buildSubject(principal: String): AuthorizingSubject {
        return AdminSubject(principal)
    }
}