package net.corda.ledger.lib.impl.stub.ledger

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.ledger.persistence.utxo.UtxoRepository
import net.corda.ledger.utxo.flow.impl.persistence.GroupParametersCache
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerGroupParametersPersistenceService
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.SignedGroupParameters
import net.corda.orm.utils.transaction
import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer
import java.time.Instant
import javax.persistence.EntityManagerFactory
import net.corda.data.membership.SignedGroupParameters as AvroGroupParameters

class StubUtxoLedgerGroupParametersPersistenceService(
    private val entityManagerFactory: EntityManagerFactory,
    private val utxoRepository: UtxoRepository,
    private val groupParametersCache: GroupParametersCache,
    private val groupParametersFactory: GroupParametersFactory,
    private val keyEncodingService: KeyEncodingService
) : UtxoLedgerGroupParametersPersistenceService {
    override fun find(hash: SecureHash): SignedGroupParameters? {
        // FIXME Do we need this at all?
        return null
    }

    override fun persistIfDoesNotExist(signedGroupParameters: SignedGroupParameters) {
        // FIXME Do we need to persist at all?
    }
}