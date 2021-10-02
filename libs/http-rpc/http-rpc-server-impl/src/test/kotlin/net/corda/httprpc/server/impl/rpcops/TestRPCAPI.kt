package net.corda.httprpc.server.impl.rpcops

import net.corda.httprpc.RpcOps

interface TestRPCAPI : RpcOps {
  fun void(): String
}

interface TestRPCAPIAnnotated : RpcOps {
  fun void(): String
}