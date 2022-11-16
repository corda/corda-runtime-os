package net.corda.membership.lib.impl

import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.membership.GroupParameters
import java.time.Instant

class GroupParametersImpl(
    private val map: LayeredPropertyMap
) : LayeredPropertyMap by map, GroupParameters {
    init {
        require(minimumPlatformVersion > 0) { "Platform version must be at least 1." }
        require(epoch > 0) { "Epoch must be at least 1." }
    }

    override val minimumPlatformVersion: Int
        get() = map.parse(MPV_KEY, Int::class.java)

    override val modifiedTime: Instant
        get() = map.parse(MODIFIED_TIME_KEY, Instant::class.java)

    override val epoch: Int
        get() = map.parse(EPOCH_KEY, Int::class.java)

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is GroupParametersImpl) return false
        if (this === other) return true
        return map == other.map
    }

    override fun hashCode(): Int {
        return map.hashCode()
    }
}