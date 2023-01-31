package net.corda.httprpc.server.impl.rest.resources.impl

import net.corda.httprpc.server.impl.rest.resources.TestRestApi
import net.corda.httprpc.server.impl.rest.resources.TestRestAPIAnnotated
import net.corda.httprpc.PluggableRestResource

class TestRestApiImpl : TestRestApi, PluggableRestResource<TestRestApi> {

  override val targetInterface: Class<TestRestApi>
    get() = TestRestApi::class.java

  override val protocolVersion: Int
    get() = 2

  override fun void() = "Sane"
}

class TestRestAPIAnnotatedImpl : TestRestAPIAnnotated, PluggableRestResource<TestRestAPIAnnotated> {
  override val targetInterface: Class<TestRestAPIAnnotated>
    get() = TestRestAPIAnnotated::class.java

  override val protocolVersion: Int
    get() = 2

  override fun void() = "Sane"
}