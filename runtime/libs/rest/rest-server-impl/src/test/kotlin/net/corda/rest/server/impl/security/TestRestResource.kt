package net.corda.rest.server.impl.security

import net.corda.rest.RestResource

internal interface TestRestResource : RestResource {
    fun dummy()
    fun dummy2()
}