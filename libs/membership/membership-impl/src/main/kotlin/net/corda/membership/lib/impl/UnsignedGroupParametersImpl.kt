package net.corda.membership.lib.impl

import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.membership.lib.NOTARIES_KEY
import net.corda.membership.lib.UnsignedGroupParameters
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.membership.NotaryInfo
import java.time.Instant

/**
 *
 * @param bytes the serialized group parameters which is the source of group parameters data.
 * @param deserializer a deserialization function for extracting the group parameters map from the serialized form.
 */
class UnsignedGroupParametersImpl(
    override val bytes: ByteArray,
    deserializer: (serialisedParams: ByteArray) -> LayeredPropertyMap
) : UnsignedGroupParameters, LayeredPropertyMap by deserializer(bytes) {

    override fun getMinimumPlatformVersion(): Int = parse(MPV_KEY, Int::class.java)
    override fun getModifiedTime(): Instant = parse(MODIFIED_TIME_KEY, Instant::class.java)
    override fun getEpoch(): Int = parse(EPOCH_KEY, Int::class.java)
    override fun getNotaries(): Collection<NotaryInfo> = parseList(NOTARIES_KEY, NotaryInfo::class.java)

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is UnsignedGroupParametersImpl) return false
        if (this === other) return true
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.hashCode()
}
