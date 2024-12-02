package net.corda.membership.lib.impl.serializer.amqp

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
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignedGroupParametersProxy) return false

        if (!groupParameters.contentEquals(other.groupParameters)) return false
        if (mgmSignature != other.mgmSignature) return false
        if (mgmSignatureSpec != other.mgmSignatureSpec) return false

        return true
    }

    override fun hashCode(): Int {
        var result = groupParameters.contentHashCode()
        result = 31 * result + mgmSignature.hashCode()
        result = 31 * result + mgmSignatureSpec.hashCode()
        return result
    }
}
