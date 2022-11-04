package net.corda.ledger.common.flow.impl.transaction.serializer.kryo

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.common.json.validation.JsonValidator
import net.corda.sandbox.type.UsedByFlow
import net.corda.serialization.checkpoint.CheckpointInput
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointOutput
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.ledger.common.transaction.PrivacySalt
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(service = [ CheckpointInternalCustomSerializer::class, UsedByFlow::class ], scope = PROTOTYPE)
class WireTransactionKryoSerializer @Activate constructor(
    @Reference(service = MerkleTreeProvider::class) private val merkleTreeProvider: MerkleTreeProvider,
    @Reference(service = DigestService::class) private val digestService: DigestService,
    @Reference(service = JsonMarshallingService::class) private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = JsonValidator::class) private val jsonValidator: JsonValidator
) : CheckpointInternalCustomSerializer<WireTransaction>, UsedByFlow {
    override val type = WireTransaction::class.java

    override fun write(output: CheckpointOutput, obj: WireTransaction) {
        output.writeClassAndObject(obj.privacySalt)
        output.writeClassAndObject(obj.componentGroupLists)
    }

    override fun read(input: CheckpointInput, type: Class<WireTransaction>): WireTransaction {
        val privacySalt = input.readClassAndObject() as PrivacySalt
        val componentGroupLists : List<List<ByteArray>> = uncheckedCast(input.readClassAndObject())
        return WireTransaction(
            merkleTreeProvider,
            digestService,
            jsonMarshallingService,
            jsonValidator,
            privacySalt,
            componentGroupLists,
        )
    }
}

