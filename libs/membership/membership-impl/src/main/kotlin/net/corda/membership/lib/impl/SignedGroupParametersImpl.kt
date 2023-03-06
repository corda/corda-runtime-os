package net.corda.membership.lib.impl

import net.corda.membership.lib.SignedGroupParameters
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.NotaryInfo

class SignedGroupParametersImpl(
    override val bytes: ByteArray,
    override val signature: DigitalSignature.WithKey,
    private val deserializer: (serialisedParams: ByteArray) -> LayeredPropertyMap
) : SignedGroupParameters {

    private val groupParameters: GroupParameters by lazy {
        UnsignedGroupParametersImpl(bytes, deserializer)
    }

    override fun getMinimumPlatformVersion() = groupParameters.minimumPlatformVersion

    override fun getModifiedTime() = groupParameters.modifiedTime

    override fun getEpoch() = groupParameters.epoch

    override fun getNotaries(): Collection<NotaryInfo> = groupParameters.notaries

    override fun getEntries(): Set<Map.Entry<String, String>> = groupParameters.entries

    override fun get(key: String) = groupParameters.get(key)

    override fun <T : Any?> parse(
        key: String,
        clazz: Class<out T>
    ) = groupParameters.parse(key, clazz)

    override fun <T : Any?> parseOrNull(
        key: String,
        clazz: Class<out T>
    ) = groupParameters.parseOrNull(key, clazz)

    override fun <T : Any?> parseList(
        itemKeyPrefix: String,
        clazz: Class<out T>
    ): List<T> = groupParameters.parseList(itemKeyPrefix, clazz)

    override fun <T : Any?> parseSet(
        itemKeyPrefix: String,
        clazz: Class<out T>
    ): Set<T> = groupParameters.parseSet(itemKeyPrefix, clazz)

}
