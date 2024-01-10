package net.corda.rest.server.impl.rest.resources

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpRestResource

@HttpRestResource(path = "nonCordaSerializable")
interface NonCordaSerializableAPI : RestResource {
  @HttpPOST
  fun call(data: NonCordaSerializableClass): String
}

class NonCordaSerializableClass(
  val data: String
)