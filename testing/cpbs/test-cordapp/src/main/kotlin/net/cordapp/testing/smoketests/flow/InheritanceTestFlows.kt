package net.cordapp.testing.smoketests.flow

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.RestStartableFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.membership.MemberInfo

interface MemberResolver {
    fun findMember(memberX500Name: String): MemberInfo?
}

@Suppress("unused")
abstract class AbstractFlow : RestStartableFlow, MemberResolver {
    private companion object {
        val log = contextLogger()
    }

    abstract fun buildOutput(memberInfo: MemberInfo?): String

    @CordaInject
    lateinit var memberLookupService: MemberLookup

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        log.info("Executing Flow...")

        try {
            val request = requestBody.getRequestBodyAs<Map<String, String>>(jsonMarshallingService)
            val memberInfoRequest = checkNotNull(request["id"]) { "Failed to find key 'id' in the RPC input args" }

            return buildOutput(findMember(memberInfoRequest))
        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow", e)
            throw e
        }
    }
}

/**
 * Used to verify that dependency injection works while using inheritance, interfaces and Corda native services.
 */
@Suppress("unused")
class DependencyInjectionTestFlow : AbstractFlow() {
    override fun buildOutput(memberInfo: MemberInfo?): String {
        return memberInfo?.name?.toString() ?: "Failed to find MemberInfo"
    }

    override fun findMember(memberX500Name: String): MemberInfo? {
        return memberLookupService.lookup(MemberX500Name.parse(memberX500Name))
    }
}
