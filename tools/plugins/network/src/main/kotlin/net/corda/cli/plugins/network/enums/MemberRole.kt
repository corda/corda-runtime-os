package net.corda.cli.plugins.network.enums

import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_ROLE

enum class MemberRole(val value: String) {
    NOTARY(NOTARY_ROLE),
}
