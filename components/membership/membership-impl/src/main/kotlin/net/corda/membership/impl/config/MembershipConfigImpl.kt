package net.corda.membership.impl.config

import net.corda.membership.config.MembershipConfig

class MembershipConfigImpl(private val map: Map<String, Any>) : MembershipConfig {

    override fun get(key: String): Any? = map[key]

    override val keys: Set<String> = map.keys
}
