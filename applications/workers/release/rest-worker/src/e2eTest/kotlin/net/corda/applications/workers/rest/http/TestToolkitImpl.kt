package net.corda.applications.workers.rest.http

import net.corda.rest.RestResource
import net.corda.rest.client.RestClient
import net.corda.rest.client.config.RestClientConfig
import java.util.concurrent.atomic.AtomicInteger

class TestToolkitImpl(private val testCaseClass: Class<Any>, private val baseAddress: String) : TestToolkit {

    private val counter = AtomicInteger()

    private val uniqueNamePrefix: String = run {
        if (testCaseClass.simpleName != "Companion") {
            testCaseClass.simpleName
        } else {
            // Converts "net.corda.applications.rest.LimitedUserAuthorizationE2eTest$Companion"
            // Into: LimitedUserAuthorizationE2eTest
            testCaseClass.name
                .substringBeforeLast('$')
                .substringAfterLast('.')

        }
        .take(15) // Also need to truncate it to avoid DB errors
    }

    /**
     * Good unique name will be:
     * "$testCaseClass-counter-currentTimeMillis"
     * [testCaseClass] will ensure traceability of the call, [counter] will help to avoid clashes within the same
     * testcase run and `currentTimeMillis` will provision for re-runs of the same test without wiping the database.
     */
    override val uniqueName: String
        get() = "$uniqueNamePrefix-${counter.incrementAndGet()}-${System.currentTimeMillis()}"

    override fun <I : RestResource> httpClientFor(restResourceClass: Class<I>, userName: String, password: String): RestClient<I> {
        return RestClient(
            baseAddress, restResourceClass, RestClientConfig()
                .enableSSL(true)
                .secureSSL(false)
                .minimumServerProtocolVersion(1)
                .username(userName)
                .password(password)
        )
    }
}