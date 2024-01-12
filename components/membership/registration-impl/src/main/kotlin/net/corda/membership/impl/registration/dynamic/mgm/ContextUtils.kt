package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.membership.lib.MemberInfoExtension

internal object ContextUtils {
    val sessionKeyRegex = String.format("${MemberInfoExtension.PARTY_SESSION_KEYS}.id", "[0-9]+").toRegex()
}
