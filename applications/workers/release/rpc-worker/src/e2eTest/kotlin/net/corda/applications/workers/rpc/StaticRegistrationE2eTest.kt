package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.http.TestToolkitProperty
import net.corda.membership.httprpc.v1.MemberRegistrationRpcOps
import net.corda.membership.httprpc.v1.types.request.MemberRegistrationRequest
import net.corda.membership.httprpc.v1.types.request.RegistrationAction
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StaticRegistrationE2eTest {
    private val testToolkit by TestToolkitProperty()

    private val nonExistentId = "failure"

    @Test
    fun `using a non-existent holding identity id results in exception during registration`() {
        testToolkit.httpClientFor(MemberRegistrationRpcOps::class.java).use { client ->
            val proxy = client.start().proxy

            val ex = assertThrows<CordaRuntimeException> {
                proxy.startRegistration(
                    MemberRegistrationRequest(
                        nonExistentId,
                        RegistrationAction.REQUEST_JOIN
                    )
                )
            }
            assert(ex.message!!.contains("MembershipRegistrationException"))
        }
    }

    @Test
    fun `using a non-existent holding identity id results in exception during registration status check`() {
        testToolkit.httpClientFor(MemberRegistrationRpcOps::class.java).use { client ->
            val proxy = client.start().proxy

            val ex = assertThrows<CordaRuntimeException> {
                proxy.checkRegistrationProgress(nonExistentId)
            }
            assert(ex.message!!.contains("MembershipRegistrationException"))
        }
    }
}