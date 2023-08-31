package net.corda.ledger.utxo.data.groupparameters.serializer.amqp

import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.SignedGroupParameters
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.crypto.SignatureSpec
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ InternalCustomSerializer::class ])
@Suppress("Unused")
class SignedGroupParametersSerializer @Activate constructor(
    @Reference(service = GroupParametersFactory::class)
    private val groupParametersFactory: GroupParametersFactory
) : BaseProxySerializer<SignedGroupParameters, SignedGroupParametersProxy>() {
    override val type
        get() = SignedGroupParameters::class.java

    override val proxyType
        get() = SignedGroupParametersProxy::class.java

    override val withInheritance
        // SignedGroupParameters is an interface.
        get() = true

    override fun toProxy(obj: SignedGroupParameters): SignedGroupParametersProxy {
        return SignedGroupParametersProxy(
            obj.groupParameters,
            obj.mgmSignature,
            obj.mgmSignatureSpec
        )
    }

    override fun fromProxy(proxy: SignedGroupParametersProxy): SignedGroupParameters {
        return groupParametersFactory.create(proxy.groupParameters, proxy.mgmSignature, proxy.mgmSignatureSpec)
    }
}

/**
 * The class that actually gets serialized on the wire.
 */
data class SignedGroupParametersProxy(
    /**
     * Properties for Signed group parameters' serialisation.
     */
    val groupParameters: ByteArray,
    val mgmSignature: DigitalSignatureWithKey,
    val mgmSignatureSpec: SignatureSpec
)
