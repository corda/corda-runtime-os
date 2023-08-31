package net.corda.cli.plugins.network.enums

import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
enum class MemberStatus(val value: String) {
    ACTIVE(MEMBER_STATUS_ACTIVE),
    SUSPENDED(MEMBER_STATUS_SUSPENDED)
}