package net.corda.httprpc.server.impl.security

import net.corda.httprpc.RpcOps

internal interface TestRpcOps : RpcOps {
    fun dummy()
    fun dummy2()
}