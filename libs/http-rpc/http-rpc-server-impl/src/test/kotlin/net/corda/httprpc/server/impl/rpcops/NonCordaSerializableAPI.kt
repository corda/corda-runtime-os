package net.corda.httprpc.server.impl.rpcops

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcResource

@HttpRpcResource(path = "nonCordaSerializable")
interface NonCordaSerializableAPI : RestResource {
  @HttpRpcPOST
  fun call(data: NonCordaSerializableClass): String
}

class NonCordaSerializableClass(
  val data: String
)