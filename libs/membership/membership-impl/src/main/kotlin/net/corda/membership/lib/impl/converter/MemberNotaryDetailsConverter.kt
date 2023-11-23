package net.corda.membership.lib.impl.converter

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.core.parseSecureHash
import net.corda.layeredpropertymap.ConversionContext
import net.corda.layeredpropertymap.CustomPropertyConverter
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.membership.lib.notary.MemberNotaryKey
import net.corda.v5.base.exceptions.ValueNotFoundException
import net.corda.v5.base.types.MemberX500Name
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Converter class, converting into a [MemberNotaryDetails] class.
 */
@Component(service = [CustomPropertyConverter::class])
class MemberNotaryDetailsConverter @Activate constructor(
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService
) : CustomPropertyConverter<MemberNotaryDetails> {
    private companion object {
        const val SERVICE_NAME = "service.name"
        const val SERVICE_BACKCHAIN_REQUIRED = "service.backchain.required"
        const val SERVICE_PROTOCOL = "service.flow.protocol.name"
        const val PROTOCOL_VERSIONS_PREFIX = "service.flow.protocol.version."
        const val KEYS_PREFIX = "keys."
        const val HASH = ".hash"
        const val PEM = ".pem"
        const val SIGNATURE_SPEC = ".signature.spec"
    }

    override val type: Class<MemberNotaryDetails>
        get() = MemberNotaryDetails::class.java

    override fun convert(context: ConversionContext): MemberNotaryDetails {
        val serviceName = context.value(SERVICE_NAME) ?: throw ValueNotFoundException("'$SERVICE_NAME' is null or absent.")
        val serviceBackchainRequired = context.value(SERVICE_BACKCHAIN_REQUIRED).toBoolean()
        val serviceProtocol = context.value(SERVICE_PROTOCOL)
        val serviceProtocolVersions = generateSequence(0) {
            it + 1
        }.map { index ->
            context.value(PROTOCOL_VERSIONS_PREFIX + index)?.toInt()
        }.takeWhile { it != null }
            .filterNotNull()
            .toList()
            .distinct()

        val keys = generateSequence(0) {
            it + 1
        }.map { index ->
            KEYS_PREFIX + index
        }.map { prefix ->
            val hash = context.value(prefix + HASH)
            val pem = context.value(prefix + PEM)
            val signatureName = context.value(prefix + SIGNATURE_SPEC)
            if ((hash != null) && (pem != null) && (signatureName != null)) {
                MemberNotaryKey(
                    publicKey = keyEncodingService.decodePublicKey(pem),
                    publicKeyHash = parseSecureHash(hash),
                    spec = SignatureSpecImpl(signatureName)
                )
            } else {
                null
            }
        }.takeWhile { it != null }
            .filterNotNull()
            .toList()
        return MemberNotaryDetails(
            serviceName = MemberX500Name.parse(serviceName),
            serviceProtocol = serviceProtocol,
            serviceProtocolVersions = serviceProtocolVersions,
            keys = keys,
            backchainRequired = serviceBackchainRequired
        )
    }
}
