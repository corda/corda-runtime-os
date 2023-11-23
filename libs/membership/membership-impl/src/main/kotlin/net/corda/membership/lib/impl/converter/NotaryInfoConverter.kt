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
 * "corda.notary.service.0.flow.protocol.name" to “ProtocolOne”
 * "corda.notary.service.0.flow.protocol.version.0" to “1”
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
        const val PROTOCOL = "flow.protocol.name"
        const val PROTOCOL_VERSIONS_PREFIX = "flow.protocol.version"
        const val KEYS_PREFIX = "keys"
        const val BACKCHAIN_REQUIRED = "backchain.required"
    }

    override val type = NotaryInfo::class.java

    override fun convert(context: ConversionContext): NotaryInfo {
        val name = context.map.parse(NAME, MemberX500Name::class.java)
        val protocol = context.value(PROTOCOL) ?: throw ValueNotFoundException("'$PROTOCOL' is null or missing.")
        val protocolVersions = context.map.parseList(PROTOCOL_VERSIONS_PREFIX, Int::class.java).toSet().apply {
            require(isNotEmpty()) { throw ValueNotFoundException("'$PROTOCOL_VERSIONS_PREFIX' is empty.") }
        }
        val keysWithWeight = context.map.parseList(KEYS_PREFIX, PublicKey::class.java).map {
            CompositeKeyNodeAndWeight(it, 1)
        }
        val backchainRequired = context.map.parseOrNull(BACKCHAIN_REQUIRED, Boolean::class.java)

        return NotaryInfoImpl(
            name,
            protocol,
            protocolVersions,
            compositeKeyProvider.create(keysWithWeight, 1),
            backchainRequired ?: true
        )
    }
}

private data class NotaryInfoImpl(
    private val name: MemberX500Name,
    private val protocol: String,
    private val protocolVersions: Collection<Int>,
    private val publicKey: PublicKey,
    private val backchainRequired: Boolean
) : NotaryInfo {
    override fun getName() = name
    override fun getProtocol() = protocol
    override fun getProtocolVersions() = protocolVersions
    override fun getPublicKey() = publicKey
    override fun getBackchainRequired() = backchainRequired
}
