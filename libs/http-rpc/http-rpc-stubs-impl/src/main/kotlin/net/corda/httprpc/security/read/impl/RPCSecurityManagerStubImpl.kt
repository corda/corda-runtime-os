package net.corda.httprpc.security.read.impl

import net.corda.httprpc.security.read.AdminSubject
import net.corda.httprpc.security.read.AuthServiceId
import net.corda.httprpc.security.read.AuthorizingSubject
import net.corda.httprpc.security.read.Password
import net.corda.httprpc.security.read.RPCSecurityManager
import org.apache.commons.lang3.StringUtils
import javax.security.auth.login.FailedLoginException

class RPCSecurityManagerStubImpl : RPCSecurityManager {

    @Volatile
    private var _isRunning = false

    override val isRunning: Boolean
        get() = _isRunning

    override fun start() {
        _isRunning = true
    }

    override fun stop() {
        _isRunning = false
    }

    override val id = AuthServiceId(RPCSecurityManagerStubImpl::class.java.name)

    override fun authenticate(principal: String, password: Password): AuthorizingSubject {
        return "admin".let {
            if (StringUtils.equalsIgnoreCase(it, principal) && password == Password(it)) {
                AdminSubject(principal)
            } else {
                throw FailedLoginException("No provisions for: $principal")
            }
        }
    }

    override fun buildSubject(principal: String): AuthorizingSubject {
        return AdminSubject(principal)
    }
}