package net.corda.httprpc.server.impl.rpcops

import net.corda.httprpc.RestResource

interface TestRPCAPI : RestResource {
  fun void(): String
}

interface TestRPCAPIAnnotated : RestResource {
  fun void(): String
}