package net.corda.flow.pipeline.sessions

import net.corda.v5.base.types.LayeredPropertyMap

class FlowSessionHeaders(private val map: LayeredPropertyMap) : LayeredPropertyMap by map {
    override fun hashCode(): Int {
        return map.hashCode()
    }

    override fun equals(other: Any?) : Boolean {
        if (other == null || other !is FlowSessionHeaders) return false
        return map == other.map
    }
}