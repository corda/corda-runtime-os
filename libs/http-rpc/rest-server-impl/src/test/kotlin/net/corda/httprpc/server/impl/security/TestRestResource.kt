package net.corda.httprpc.server.impl.security

import net.corda.httprpc.RestResource

internal interface TestRestResource : RestResource {
    fun dummy()
    fun dummy2()
}