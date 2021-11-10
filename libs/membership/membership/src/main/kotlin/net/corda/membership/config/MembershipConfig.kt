package net.corda.membership.config

interface MembershipConfig {

    operator fun get(key: String): Any?

    val keys: Set<String>
}

inline fun <reified T> MembershipConfig.getValue(key: String): T = this[key] as T
