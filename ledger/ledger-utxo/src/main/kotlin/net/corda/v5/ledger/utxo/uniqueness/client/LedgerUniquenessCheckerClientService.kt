package net.corda.v5.ledger.utxo.uniqueness.client

import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import java.time.Instant
import java.util.concurrent.Future

/**
 * This service is used to request uniqueness checking. The request will be processed asynchronously
 * and a [Future] will be returned that can be used to check the outcome. This service can be injected
 * to either flows or other services.
 */
@DoNotImplement
interface LedgerUniquenessCheckerClientService {

    /**
     * @param txId The ID of the transaction that needs processing
     *
     * @param inputStates A list of the input state refs that belongs to the given transaction in a
     * <TX_ID>:<STATE_NUM> format. This list might be empty if the given transaction is only doing
     * issuance.
     *
     * @param referenceStates A list of the reference state refs that belongs to the given transaction,
     * in a <TX_ID>:<STATE_NUM> format. This list might be empty.
     *
     * @param numOutputStates The number of output states the given transaction has.
     *
     * @param timeWindowLowerBound The earliest date/time from which the transaction is considered valid.
     * This is an optional parameter.
     *
     * @param timeWindowUpperBound The latest date/time until the transaction is considered valid.
     *
     * @return The result that was produced by the uniqueness checker.
     */
    @Suppress("LongParameterList")
    @Suspendable
    fun requestUniquenessCheck(
        txId: String,
        inputStates: List<String>,
        referenceStates: List<String>,
        numOutputStates: Int,
        timeWindowLowerBound: Instant?,
        timeWindowUpperBound: Instant
    ): UniquenessCheckResult
}

/**
 * An extension function that can be called using actual [UniquenessCheckStateRef] objects as input and
 * reference states rather than [String]s.
 */
@Suppress("LongParameterList")
@Suspendable
fun LedgerUniquenessCheckerClientService.requestUniquenessCheck(
    txId: String,
    inputStates: List<UniquenessCheckStateRef>,
    referenceStates: List<UniquenessCheckStateRef>,
    numOutputStates: Int,
    timeWindowLowerBound: Instant?,
    timeWindowUpperBound: Instant
) = requestUniquenessCheck(
    txId,
    inputStates.map { it.toString() },
    referenceStates.map { it.toString() },
    numOutputStates,
    timeWindowLowerBound,
    timeWindowUpperBound
)
