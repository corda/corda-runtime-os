package net.corda.membership.lib.impl.converter

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.PublicKeyHash
import net.corda.layeredpropertymap.ConversionContext
import net.corda.layeredpropertymap.CustomPropertyConverter
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.membership.lib.notary.MemberNotaryKey
import net.corda.v5.base.exceptions.ValueNotFoundException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SignatureSpec
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
        const val SERVICE_PLUGIN = "service.plugin"
        const val KEYS_PREFIX = "keys."
        const val HASH = ".hash"
        const val PEM = ".pem"
        const val SIGNATURE_SPEC = ".signature.spec"
    }

    override val type: Class<MemberNotaryDetails>
        get() = MemberNotaryDetails::class.java

    override fun convert(context: ConversionContext): MemberNotaryDetails {
        val serviceName = context.value(SERVICE_NAME) ?: throw ValueNotFoundException("'$SERVICE_NAME' is null or absent.")
        val servicePlugin = context.value(SERVICE_PLUGIN)
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
                    publicKeyHash = PublicKeyHash.parse(hash),
                    spec = SignatureSpec(signatureName)
                )
            } else {
                null
            }
        }.takeWhile { it != null }
            .filterNotNull()
            .toList()
        return MemberNotaryDetails(
            serviceName = MemberX500Name.parse(serviceName),
            servicePlugin = servicePlugin,
            keys = keys
        )
    }
}
