package net.corda.ledger.common.testkit

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeFactoryImpl
import net.corda.ledger.common.impl.transaction.TransactionMetaData
import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.ledger.common.impl.transaction.WireTransactionDigestSettings
import net.corda.ledger.common.testkit.PrivacySaltImplExample.Companion.getPrivacySaltImpl
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory

class WireTransactionExample {
    companion object{
        fun getWireTransaction(
            schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl(),
            digestService: DigestService = DigestServiceImpl(schemeMetadata, null),
            merkleTreeFactory: MerkleTreeFactory = MerkleTreeFactoryImpl(digestService)
        ): WireTransaction{
            val mapper = jacksonObjectMapper()
            val transactionMetaData = TransactionMetaData(
                mapOf(
                    TransactionMetaData.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues
                )
            )
            val componentGroupLists = listOf(
                listOf(mapper.writeValueAsBytes(transactionMetaData)), // TODO(update with CORE-5940)
                listOf(".".toByteArray()),
                listOf("abc d efg".toByteArray()),
            )
            return WireTransaction(
                merkleTreeFactory,
                digestService,
                getPrivacySaltImpl(),
                componentGroupLists
            )
        }
    }
}

