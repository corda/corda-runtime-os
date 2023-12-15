package net.corda.ledger.utxo.flow.impl.serializer.kryo

import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.SignedGroupParameters
import net.corda.sandbox.type.SandboxConstants
import net.corda.sandbox.type.UsedByFlow
import net.corda.serialization.checkpoint.CheckpointInput
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointOutput
import net.corda.v5.crypto.SignatureSpec
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [ CheckpointInternalCustomSerializer::class, UsedByFlow::class ],
    property = [SandboxConstants.CORDA_UNINJECTABLE_SERVICE],
    scope = ServiceScope.PROTOTYPE
)
class SignedGroupParametersKryoSerializer @Activate constructor(
    @Reference(service = GroupParametersFactory::class)
    private val groupParametersFactory: GroupParametersFactory
) : CheckpointInternalCustomSerializer<SignedGroupParameters>, UsedByFlow {
    override val type: Class<SignedGroupParameters> get() = SignedGroupParameters::class.java

    override fun write(output: CheckpointOutput, obj: SignedGroupParameters) {
        output.writeClassAndObject(obj.groupParameters)
        output.writeClassAndObject(obj.mgmSignature)
        output.writeClassAndObject(obj.mgmSignatureSpec)
    }

    override fun read(input: CheckpointInput, type: Class<out SignedGroupParameters>): SignedGroupParameters {
        val bytes = input.readClassAndObject() as ByteArray
        val signature = input.readClassAndObject() as DigitalSignatureWithKey
        val signatureSpec = input.readClassAndObject() as SignatureSpec
        return groupParametersFactory.create(bytes, signature, signatureSpec)
    }
}
