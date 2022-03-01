package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.http.TestToolkitProperty
import net.corda.membership.httprpc.v1.MemberRegistrationRpcOps
import net.corda.membership.httprpc.v1.types.request.MemberRegistrationRequest
import net.corda.membership.httprpc.v1.types.request.RegistrationAction
import org.junit.jupiter.api.Test

class StaticRegistrationE2eTest {
    private val testToolkit by TestToolkitProperty()

    @Test
    fun test() {
        testToolkit.httpClientFor(MemberRegistrationRpcOps::class.java).use { client ->
            val proxy = client.start().proxy

            proxy.startRegistration(
                MemberRegistrationRequest(
                    "test",
                    RegistrationAction.REQUEST_JOIN
                )
            )

        }
    }
}