package net.corda.membership.lib.impl

import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.membership.lib.NOTARIES_KEY
import net.corda.membership.lib.UnsignedGroupParameters
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.membership.NotaryInfo
import java.time.Instant

class UnsignedGroupParametersImpl(
    override val bytes: ByteArray,
    private val deserializer: (serialisedParams: ByteArray) -> LayeredPropertyMap
) : UnsignedGroupParameters {
//    init {
//        require(minimumPlatformVersion > 0) { "Platform version must be at least 1." }
//        require(epoch > 0) { "Epoch must be at least 1." }
//    }

    private val map: LayeredPropertyMap by lazy {
        deserializer(bytes)
    }

    override fun getMinimumPlatformVersion(): Int = map.parse(MPV_KEY, Int::class.java)
    override fun getModifiedTime(): Instant = map.parse(MODIFIED_TIME_KEY, Instant::class.java)
    override fun getEpoch(): Int = map.parse(EPOCH_KEY, Int::class.java)
    override fun getNotaries(): Collection<NotaryInfo> = map.parseList(NOTARIES_KEY, NotaryInfo::class.java)

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is UnsignedGroupParametersImpl) return false
        if (this === other) return true
        return map == other.map
    }

    override fun hashCode(): Int = map.hashCode()

    override fun getEntries(): Set<Map.Entry<String, String>> = map.entries

    override fun get(
        key: String
    ): String? = map.get(key)

    override fun <T : Any?> parse(
        key: String,
        clazz: Class<out T>
    ) = map.parse(key, clazz)

    override fun <T : Any?> parseOrNull(
        key: String,
        clazz: Class<out T>
    ): T? = map.parseOrNull(key, clazz)

    override fun <T : Any?> parseList(
        itemKeyPrefix: String,
        clazz: Class<out T>
    ): List<T> = map.parseList(itemKeyPrefix, clazz)

    override fun <T : Any?> parseSet(
        itemKeyPrefix: String,
        clazz: Class<out T>
    ): Set<T> = map.parseSet(itemKeyPrefix, clazz)
}
