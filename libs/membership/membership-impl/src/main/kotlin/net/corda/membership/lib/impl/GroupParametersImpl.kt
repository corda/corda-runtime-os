package net.corda.membership.lib.impl

import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.membership.lib.NOTARIES_KEY
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.NotaryInfo
import java.time.Instant

class GroupParametersImpl(
    private val map: LayeredPropertyMap
) : LayeredPropertyMap by map, GroupParameters {
    init {
        require(minimumPlatformVersion > 0) { "Platform version must be at least 1." }
        require(epoch > 0) { "Epoch must be at least 1." }
    }

    override fun getMinimumPlatformVersion(): Int = map.parse(MPV_KEY, Int::class.java)
    override fun getModifiedTime(): Instant = map.parse(MODIFIED_TIME_KEY, Instant::class.java)
    override fun getEpoch(): Int = map.parse(EPOCH_KEY, Int::class.java)
    override fun getNotaries(): Collection<NotaryInfo> = map.parseList(NOTARIES_KEY, NotaryInfo::class.java)

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is GroupParametersImpl) return false
        if (this === other) return true
        return map == other.map
    }

    override fun hashCode(): Int {
        return map.hashCode()
    }
}
