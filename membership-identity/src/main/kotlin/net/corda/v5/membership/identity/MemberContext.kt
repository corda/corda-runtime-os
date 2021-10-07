package net.corda.v5.membership.identity

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
interface MemberContext {

    operator fun get(key: String): Any?

    val keys: Set<String>
}

inline fun <reified T> MemberContext.getValue(key: String): T = this[key] as T