package net.corda.httprpc.security.read.impl

import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.security.read.RPCSecurityManagerFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component
class RPCSecurityManagerFactoryStubImpl @Activate constructor() : RPCSecurityManagerFactory {

    override fun createRPCSecurityManager(): RPCSecurityManager {
        return RPCSecurityManagerStubImpl()
    }
}