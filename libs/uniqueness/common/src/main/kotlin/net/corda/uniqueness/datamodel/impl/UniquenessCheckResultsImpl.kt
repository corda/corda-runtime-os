package net.corda.uniqueness.datamodel.impl

import net.corda.data.uniqueness.UniquenessCheckResponseAvro
import net.corda.data.uniqueness.UniquenessCheckResultInputStateConflictAvro
import net.corda.data.uniqueness.UniquenessCheckResultInputStateUnknownAvro
import net.corda.data.uniqueness.UniquenessCheckResultMalformedRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResultReferenceStateConflictAvro
import net.corda.data.uniqueness.UniquenessCheckResultReferenceStateUnknownAvro
import net.corda.data.uniqueness.UniquenessCheckResultSuccessAvro
import net.corda.data.uniqueness.UniquenessCheckResultTimeWindowOutOfBoundsAvro
import net.corda.uniqueness.datamodel.common.UniquenessConstants.RESULT_ACCEPTED_REPRESENTATION
import net.corda.uniqueness.datamodel.common.UniquenessConstants.RESULT_REJECTED_REPRESENTATION
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckError
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckErrorGeneral
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckErrorInputStateConflict
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckErrorInputStateUnknown
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckErrorReferenceStateConflict
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckErrorReferenceStateUnknown
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckErrorTimeWindowOutOfBounds
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckResult
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckResultFailure
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckResultSuccess
import org.apache.avro.specific.SpecificRecord
import java.time.Instant

data class UniquenessCheckResultSuccessImpl(
    override val resultTimestamp: Instant
) : UniquenessCheckResultSuccess

data class UniquenessCheckResultFailureImpl(
    override val resultTimestamp: Instant,
    override val error: UniquenessCheckError
) : UniquenessCheckResultFailure