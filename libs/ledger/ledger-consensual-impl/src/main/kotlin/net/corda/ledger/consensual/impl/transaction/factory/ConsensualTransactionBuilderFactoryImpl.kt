package net.corda.ledger.consensual.impl.transaction.factory

import net.corda.flow.fiber.FlowFiberService
import net.corda.ledger.common.impl.transaction.TransactionMetaData
import net.corda.ledger.common.impl.transaction.TransactionMetaData.Companion.DIGEST_SETTINGS_KEY
import net.corda.ledger.common.impl.transaction.TransactionMetaData.Companion.LEDGER_MODEL_KEY
import net.corda.ledger.common.impl.transaction.TransactionMetaData.Companion.LEDGER_VERSION_KEY
import net.corda.ledger.common.impl.transaction.TransactionMetaData.Companion.PLATFORM_VERSION_KEY
import net.corda.ledger.common.impl.transaction.WireTransactionDigestSettings
import net.corda.ledger.consensual.impl.transaction.ConsensualLedgerTransactionImpl
import net.corda.ledger.consensual.impl.transaction.ConsensualTransactionBuilderImpl
import net.corda.ledger.consensual.impl.transaction.TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Suppress("LongParameterList")
@Component(service = [ConsensualTransactionBuilderFactory::class], scope = ServiceScope.PROTOTYPE)
class ConsensualTransactionBuilderFactoryImpl @Activate constructor(
    @Reference(service = CipherSchemeMetadata::class) private val cipherSchemeMetadata: CipherSchemeMetadata,
    @Reference(service = DigestService::class) private val digestService: DigestService,
    @Reference(service = JsonMarshallingService::class) private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = MerkleTreeProvider::class) private val merkleTreeProvider: MerkleTreeProvider,
    @Reference(service = SerializationService::class) private val serializationService: SerializationService,
    @Reference(service = SigningService::class) private val signingService: SigningService,
    @Reference(service = MemberLookup::class) private val memberLookup: MemberLookup,
    @Reference(service = FlowFiberService::class) private val flowFiberService: FlowFiberService
) : ConsensualTransactionBuilderFactory {

    // TODO(CORE-5940 set CPK identifier/etc)
    private fun calculateTransactionMetaData(/*sandboxCpks: List<CpkIdentifier>*/): TransactionMetaData {
        return TransactionMetaData(
            linkedMapOf(
                LEDGER_MODEL_KEY to ConsensualLedgerTransactionImpl::class.java.canonicalName,
                LEDGER_VERSION_KEY to TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION,
                DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
                PLATFORM_VERSION_KEY to memberLookup.myInfo().platformVersion,
            )
        )
    }

    override fun create(): ConsensualTransactionBuilder {
        /*val sandboxCpks = */flowFiberService
            .getExecutingFiber()
            .getExecutionContext()
            .sandboxGroupContext
            .sandboxGroup
            .metadata
            .values
            .filter{ it.isContractCpk() }
            .map{ it.cpkId }
            .toList()

        return ConsensualTransactionBuilderImpl(
            cipherSchemeMetadata,
            digestService,
            jsonMarshallingService,
            merkleTreeProvider,
            serializationService,
            signingService,
            calculateTransactionMetaData(/*sandboxCpks*/),
            getCpiIdentifier()
        )
    }
}

/**
 * TODO(Fake values until we can get CPI information properly)
 * This is called in multiple places.
 */
fun getCpiIdentifier(): CpiIdentifier = CpiIdentifier(
    "CPI name",
    "CPI version",
    SecureHash("SHA-256", "Fake-value".toByteArray())
)