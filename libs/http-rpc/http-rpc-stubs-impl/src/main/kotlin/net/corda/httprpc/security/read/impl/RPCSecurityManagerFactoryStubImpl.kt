package net.corda.httprpc.security.read.impl

import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.security.read.RPCSecurityManagerFactory
import org.osgi.service.component.annotations.Component

@Component(immediate = true, service = [RPCSecurityManagerFactory::class])
class RPCSecurityManagerFactoryStubImpl : RPCSecurityManagerFactory {

    override fun createRPCSecurityManager(): RPCSecurityManager {
        return RPCSecurityManagerStubImpl()
    }
}