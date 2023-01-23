package net.corda.httprpc.server.impl.rpcops.impl

import net.corda.httprpc.server.impl.rpcops.TestRPCAPI
import net.corda.httprpc.server.impl.rpcops.TestRPCAPIAnnotated
import net.corda.httprpc.PluggableRestResource

class TestRPCAPIImpl : TestRPCAPI, PluggableRestResource<TestRPCAPI> {

  override val targetInterface: Class<TestRPCAPI>
    get() = TestRPCAPI::class.java

  override val protocolVersion: Int
    get() = 2

  override fun void() = "Sane"
}

class TestRPCAPIAnnotatedImpl : TestRPCAPIAnnotated, PluggableRestResource<TestRPCAPIAnnotated> {
  override val targetInterface: Class<TestRPCAPIAnnotated>
    get() = TestRPCAPIAnnotated::class.java

  override val protocolVersion: Int
    get() = 2

  override fun void() = "Sane"
}