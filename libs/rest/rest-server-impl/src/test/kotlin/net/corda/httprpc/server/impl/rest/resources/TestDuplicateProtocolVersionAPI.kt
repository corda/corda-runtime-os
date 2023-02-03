package net.corda.httprpc.server.impl.rest.resources

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpGET
import net.corda.httprpc.annotations.HttpRestResource

@HttpRestResource
interface TestDuplicateProtocolVersionAPI : RestResource {
    @HttpGET(path = "getProtocolVersion")
    fun test(): String
}