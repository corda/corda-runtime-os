package net.corda.membership.impl.registration.dummy

import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.membership.lib.grouppolicy.MemberGroupPolicy
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory

interface TestGroupPolicyParser : GroupPolicyParser {
    fun loadMgm(holdingIdentity: HoldingIdentity, mgm: MemberInfo)
}

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [GroupPolicyParser::class, TestGroupPolicyParser::class])
class TestGroupPolicyParserImpl @Activate constructor() : TestGroupPolicyParser {
    private var cachedMgm: MemberInfo? = null

    private companion object {
        const val UNIMPLEMENTED_FUNCTION = "Called unimplemented function for test service."
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun loadMgm(holdingIdentity: HoldingIdentity, mgm: MemberInfo) {
        cachedMgm = mgm
    }

    override fun parse(
        holdingIdentity: HoldingIdentity,
        groupPolicy: String?,
        groupPolicyPropertiesQuery: () -> LayeredPropertyMap?
    ): GroupPolicy {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun parseMember(groupPolicy: String): MemberGroupPolicy? {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun getMgmInfo(holdingIdentity: HoldingIdentity, groupPolicy: String): MemberInfo? = cachedMgm
}
