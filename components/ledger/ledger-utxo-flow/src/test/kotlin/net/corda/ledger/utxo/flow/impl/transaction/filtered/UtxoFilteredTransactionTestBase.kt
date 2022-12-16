package net.corda.ledger.utxo.flow.impl.transaction.filtered

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.common.json.validation.impl.JsonValidatorImpl
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.impl.transaction.filtered.factory.FilteredTransactionFactoryImpl
import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.ledger.common.flow.transaction.filtered.factory.ComponentGroupFilterParameters
import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

open class UtxoFilteredTransactionTestBase {
    companion object {
        val SIGNATORY_1 = "signatory 1".toByteArray()
        val SIGNATORY_2 = "signatory 2".toByteArray()
        val STATE_REF_1 = "state ref 1".toByteArray()
        val STATE_REF_2 = "state ref 2".toByteArray()
        val STATE_REF_3 = "state ref 3".toByteArray()
        val OUTPUT_STATE_1 = "output state 1".toByteArray()
        val OUTPUT_INFO_1 = "output info 1".toByteArray()
        val OUTPUT_STATE_2 = "output state 2".toByteArray()
        val OUTPUT_INFO_2 = "output info 2".toByteArray()
        val NOTARY = "notary".toByteArray()
        val TIME_WINDOW = "time window".toByteArray()
        val COMMAND = "command".toByteArray()
        val COMMAND_INFO = "command_info".toByteArray()

        val inputId = SecureHash.parse("SHA-256:1234567890")
        val notary = Party(MemberX500Name.parse("O=notary, L=London, C=GB"), mock())
        val timeWindow = mock<TimeWindow>()

        val signerKey1 = mock<PublicKey>()
        val signerKey2 = mock<PublicKey>()

        val outputInfo1 = UtxoOutputInfoComponent(null, null, notary, "", "")
        val outputInfo2 = UtxoOutputInfoComponent("three", 3, notary, "", "")
    }

    lateinit var wireTransaction: WireTransaction
    lateinit var filteredTransaction: FilteredTransaction

    val digestService =
        DigestServiceImpl(PlatformDigestServiceImpl(CipherSchemeMetadataImpl()), null)
    protected val jsonMarshallingService = JsonMarshallingServiceImpl()
    protected val jsonValidator = JsonValidatorImpl()
    protected val merkleTreeProvider = MerkleTreeProviderImpl(digestService)
    val serializationService = mock<SerializationService>()

    protected val filteredTransactionFactory = FilteredTransactionFactoryImpl(
        jsonMarshallingService,
        merkleTreeProvider,
        serializationService
    )

    @BeforeEach
    fun beforeEach() {
        whenever(serializationService.deserialize(NOTARY, Party::class.java)).thenReturn(notary)
        whenever(serializationService.deserialize(NOTARY, Any::class.java)).thenReturn(notary)
        whenever(serializationService.deserialize(SIGNATORY_1, PublicKey::class.java)).thenReturn(signerKey1)
        whenever(serializationService.deserialize(SIGNATORY_2, PublicKey::class.java)).thenReturn(signerKey2)
        whenever(serializationService.deserialize(TIME_WINDOW, TimeWindow::class.java)).thenReturn(timeWindow)
        whenever(serializationService.deserialize(TIME_WINDOW, Any::class.java)).thenReturn(timeWindow)
        whenever(serializationService.deserialize(OUTPUT_INFO_1, UtxoOutputInfoComponent::class.java)).thenReturn(
            outputInfo1
        )
        whenever(serializationService.deserialize(OUTPUT_INFO_2, UtxoOutputInfoComponent::class.java)).thenReturn(
            outputInfo2
        )
        whenever(serializationService.deserialize(COMMAND_INFO, Any::class.java)).thenReturn(listOf(""))
        whenever(serializationService.deserialize(STATE_REF_1, StateRef::class.java)).thenReturn(StateRef(inputId, 0))
        whenever(serializationService.deserialize(STATE_REF_2, StateRef::class.java)).thenReturn(StateRef(inputId, 5))
        whenever(serializationService.deserialize(STATE_REF_3, StateRef::class.java)).thenReturn(StateRef(inputId, 3))
        whenever(serializationService.deserialize(OUTPUT_STATE_1, ContractState::class.java)).thenReturn(OutputState1())
        whenever(serializationService.deserialize(OUTPUT_STATE_2, ContractState::class.java)).thenReturn(OutputState2())
        whenever(serializationService.deserialize(COMMAND, Any::class.java)).thenReturn(mock<Command>())

        wireTransaction = getWireTransactionExample(
            digestService,
            merkleTreeProvider,
            jsonMarshallingService,
            jsonValidator,
            componentGroupLists = listOf(
                listOf(NOTARY, TIME_WINDOW),
                listOf(SIGNATORY_1, SIGNATORY_2),
                listOf(OUTPUT_INFO_1, OUTPUT_INFO_2),
                listOf(COMMAND_INFO),
                emptyList(),
                listOf(STATE_REF_1, STATE_REF_2),
                listOf(STATE_REF_3),
                listOf(OUTPUT_STATE_1, OUTPUT_STATE_2),
                listOf(COMMAND)
            )
        )

        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.METADATA.ordinal,
                    TransactionMetadataImpl::class.java
                ) { true },
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.NOTARY.ordinal, Any::class.java) { true },
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS_INFO.ordinal,
                    UtxoOutputInfoComponent::class.java
                ) { true },
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS_INFO.ordinal),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.INPUTS.ordinal, StateRef::class.java) { true },
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS.ordinal,
                    ContractState::class.java
                ) { true },
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS.ordinal),
            )
        )

    }

}