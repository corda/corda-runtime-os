package net.corda.rest.server.impl.rest.resources

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpRestResource

@HttpRestResource
interface TestDuplicateProtocolVersionAPI : RestResource {
    @HttpGET(path = "getProtocolVersion")
    fun test(): String
}