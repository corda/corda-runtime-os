package net.corda.httprpc.security.read.impl

import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.security.read.RPCSecurityManagerFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope

@Component(service = [RPCSecurityManagerFactory::class], scope = ServiceScope.SINGLETON)
class RPCSecurityManagerFactoryStubImpl @Activate constructor() : RPCSecurityManagerFactory {

    override fun createRPCSecurityManager(): RPCSecurityManager {
        return RPCSecurityManagerStubImpl()
    }
}