package net.corda.membership.lib.impl.converter

import net.corda.crypto.core.CompositeKeyProvider
import net.corda.layeredpropertymap.ConversionContext
import net.corda.layeredpropertymap.CustomPropertyConverter
import net.corda.v5.base.exceptions.ValueNotFoundException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import net.corda.v5.membership.NotaryInfo
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.PublicKey

/**
 * Converts notary service information into [NotaryInfo] objects.
 * Example property map for a notary service:
 * "corda.notary.service.0.name" to “NotaryService”
 * "corda.notary.service.0.plugin" to “PluginOne”
 * "corda.notary.service.0.keys.0” to “encoded_key_0”
 * "corda.notary.service.0.keys.1” to “encoded-key_1”
 */
@Component(service = [CustomPropertyConverter::class])
class NotaryInfoConverter @Activate constructor(
    @Reference(service = CompositeKeyProvider::class)
    private val compositeKeyProvider: CompositeKeyProvider,
) : CustomPropertyConverter<NotaryInfo> {
    private companion object {
        const val NAME = "name"
        const val PLUGIN = "plugin"
        const val KEYS_PREFIX = "keys"
    }

    override val type = NotaryInfo::class.java

    override fun convert(context: ConversionContext): NotaryInfo {
        val name = context.map.parse(NAME, MemberX500Name::class.java)
        val pluginName = context.value(PLUGIN) ?: throw ValueNotFoundException("'$PLUGIN' is null or missing.")
        val keysWithWeight = context.map.parseList(KEYS_PREFIX, PublicKey::class.java).map {
            CompositeKeyNodeAndWeight(it, 1)
        }
        return NotaryInfoImpl(
            name,
            pluginName,
            compositeKeyProvider.create(keysWithWeight, null)
        )
    }
}

private data class NotaryInfoImpl(
    private val name: MemberX500Name,
    private val pluginClass: String,
    private val publicKey: PublicKey,
) : NotaryInfo {
    override fun getName() = name
    override fun getPluginClass() = pluginClass
    override fun getPublicKey() = publicKey
}
