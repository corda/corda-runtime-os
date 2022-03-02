package net.corda.membership.impl

import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.membership.MGMContext


class MGMContextImpl(
    private val map: LayeredPropertyMap
) : LayeredPropertyMap by map, MGMContext {
    override fun hashCode(): Int {
        return map.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is MGMContextImpl) return false
        return map == other.map
    }
}