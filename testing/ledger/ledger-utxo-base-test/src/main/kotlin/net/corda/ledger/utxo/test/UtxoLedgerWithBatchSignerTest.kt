package net.corda.ledger.utxo.test

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.flow.application.crypto.SignatureSpecServiceImpl
import net.corda.ledger.common.flow.impl.transaction.TransactionSignatureServiceImpl
import net.corda.ledger.common.flow.transaction.TransactionSignatureVerificationServiceInternal
import net.corda.ledger.common.testkit.FakePlatformInfoProvider
import net.corda.ledger.common.testkit.keyPairExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.flows.FlowContextPropertyKeys
import net.corda.v5.application.flows.FlowEngine
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PrivateKey
import java.security.Signature

/**
 * Extension of [UtxoLedgerTest] base class with [singingService] service. The service can sign a batch of transactions
 * (including size 1). This allows to have a proof [net.corda.v5.crypto.merkle.MerkleProof] inside the signature
 * [DigitalSignatureAndMetadata].
 * The signing uses [net.corda.crypto.cipher.suite.SignatureSpecs.ECDSA_SHA256] algorithm.
 */
abstract class UtxoLedgerWithBatchSignerTest : UtxoLedgerTest() {

    val singingService = TransactionSignatureServiceImpl(serializationServiceWithWireTx,
        signingService = mock<SigningService>().also {
            whenever(it.findMySigningKeys(any())).thenReturn(mapOf(publicKeyExample to publicKeyExample))
            whenever(
                it.sign(any(), any(), any())
            ).thenAnswer{
                val signableData = it.arguments.first() as ByteArray
                DigitalSignatureWithKeyId(
                    publicKeyExample.fullIdHash(),
                    signData(signableData, keyPairExample.private)
            )
        }
        },
        signatureSpecService = SignatureSpecServiceImpl(CipherSchemeMetadataImpl()),
        merkleTreeProvider = MerkleTreeProviderImpl(digestService),
        platformInfoProvider = FakePlatformInfoProvider(),
        flowEngine = mock<FlowEngine>().also {
            whenever(it.flowContextProperties).thenReturn(object : FlowContextProperties {
                override fun put(key: String, value: String) {
                    throw NotImplementedError("Not envisioned to invoked by tests.")
                }

                override fun get(key: String): String =
                    when (key) {
                        FlowContextPropertyKeys.CPI_NAME -> "Cordapp1"
                        FlowContextPropertyKeys.CPI_VERSION -> "1"
                        FlowContextPropertyKeys.CPI_SIGNER_SUMMARY_HASH -> "hash1234"
                        else -> "1213213213" //FlowContextPropertyKeys.CPI_FILE_CHECKSUM
                    }
            })
        },
        transactionSignatureVerificationServiceInternal = mock<TransactionSignatureVerificationServiceInternal>()
    )

    private fun signData(data: ByteArray, privateKey: PrivateKey): ByteArray {
        val signature = Signature.getInstance(SignatureSpecs.ECDSA_SHA256.signatureName)
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }
}
