package net.corda.httprpc.server.impl.rpcops

import net.corda.v5.httprpc.api.RpcOps

interface TestRPCAPI : RpcOps {
  fun void(): String
}

interface TestRPCAPIAnnotated : RpcOps {
  fun void(): String
}