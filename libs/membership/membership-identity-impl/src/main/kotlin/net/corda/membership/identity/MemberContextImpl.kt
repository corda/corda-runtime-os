package net.corda.membership.identity

import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.membership.identity.MemberContext

class MemberContextImpl(
    private val map: LayeredPropertyMap
) : LayeredPropertyMap by map, MemberContext {
    override fun hashCode(): Int {
        return map.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is MemberContextImpl) return false
        return map == other.map
    }
}

