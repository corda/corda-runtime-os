package net.corda.applications.workers.rest.http

import net.corda.applications.workers.rest.utils.AdminPasswordUtil.adminPassword
import net.corda.applications.workers.rest.utils.AdminPasswordUtil.adminUser
import net.corda.httprpc.RestResource
import net.corda.httprpc.client.RestClient

/**
 * Toolkit for REST E2E tests execution
 */
interface TestToolkit {

    /**
     * Creates easily attributable to a testcase unique name
     */
    val uniqueName: String

    /**
     * Creates the [RestClient] for a given [RestResource] class.
     */
    fun <I : RestResource> httpClientFor(
        rpcOpsClass: Class<I>,
        userName: String = adminUser,
        password: String = adminPassword
    ): RestClient<I>
}