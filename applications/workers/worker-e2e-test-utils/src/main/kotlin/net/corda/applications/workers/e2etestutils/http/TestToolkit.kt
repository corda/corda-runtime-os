package net.corda.applications.workers.e2etestutils.http

import net.corda.applications.workers.e2etestutils.utils.AdminPasswordUtil.adminPassword
import net.corda.applications.workers.e2etestutils.utils.AdminPasswordUtil.adminUser
import net.corda.rest.RestResource
import net.corda.rest.client.RestClient

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
        restResourceClass: Class<I>,
        userName: String = adminUser,
        password: String = adminPassword
    ): RestClient<I>
}