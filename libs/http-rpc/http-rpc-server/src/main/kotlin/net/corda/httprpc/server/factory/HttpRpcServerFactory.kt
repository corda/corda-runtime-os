package net.corda.httprpc.server.factory

import net.corda.httprpc.server.HttpRpcServer

interface HttpRpcServerFactory {

    fun createHttpRpcServer(): HttpRpcServer
}