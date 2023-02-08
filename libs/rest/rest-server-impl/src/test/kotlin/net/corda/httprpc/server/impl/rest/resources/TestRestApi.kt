package net.corda.httprpc.server.impl.rest.resources

import net.corda.httprpc.RestResource

interface TestRestApi : RestResource {
  fun void(): String
}

interface TestRestAPIAnnotated : RestResource {
  fun void(): String
}