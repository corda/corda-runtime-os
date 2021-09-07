package net.corda.httprpc.server.factory

import net.corda.httprpc.server.HttpRpcServerImpl
import org.osgi.service.component.annotations.Component

@Component
class HttpRpcServerFactoryImpl : HttpRpcServerFactory {

    override fun createHttpRpcServer(): HttpRpcServerImpl {



    }
}