package net.cordapp.testing.smoketests.flow

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
import org.slf4j.LoggerFactory

interface MemberResolver {
    fun findMember(memberX500Name: String): MemberInfo?
}

@Suppress("unused")
abstract class AbstractFlow : ClientStartableFlow, MemberResolver {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    abstract fun buildOutput(memberInfo: MemberInfo?): String

    @CordaInject
    lateinit var memberLookupService: MemberLookup

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Executing Flow...")

        try {
            val request = requestBody.getRequestBodyAsMap(jsonMarshallingService, String::class.java, String::class.java)
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
