package net.corda.rest.server.impl.rest.resources

import net.corda.rest.RestResource

interface TestRestApi : RestResource {
  fun void(): String
}

interface TestRestAPIAnnotated : RestResource {
  fun void(): String
}