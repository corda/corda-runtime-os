package net.corda.httprpc.server.impl.rpcops

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.HttpRestResource

@HttpRestResource(path = "nonCordaSerializable")
interface NonCordaSerializableAPI : RestResource {
  @HttpPOST
  fun call(data: NonCordaSerializableClass): String
}

class NonCordaSerializableClass(
  val data: String
)