package net.corda.uniqueness.utils

import net.corda.ledger.libs.uniqueness.data.UniquenessCheckRequest
import net.corda.ledger.libs.uniqueness.data.UniquenessHoldingIdentity
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.crypto.SecureHash
import java.time.Instant

class UniquenessCheckRequestBuilder(
    txId: SecureHash,
    defaultGroupId: String,
    defaultTimeWindowUpperBound: Instant,
    defaultHoldingIdentity: UniquenessHoldingIdentity? = null
) {

    // Default holding id used in most tests
    private val defaultNotaryVNodeHoldingIdentity = createTestHoldingIdentity(
        "C=GB, L=London, O=NotaryRep1", defaultGroupId
    ).let { UniquenessHoldingIdentity(it.x500Name, it.groupId, it.shortHash, it.hash) }

    private val originatorX500Name = "C=GB, L=London, O=Alice"

    private var uniquenessCheckType: UniquenessCheckRequest.Type = UniquenessCheckRequest.Type.WRITE

    private var transactionId: String = txId.toString()

    private var initiator: String = originatorX500Name
    private var holdingIdentity: UniquenessHoldingIdentity = defaultHoldingIdentity ?: defaultNotaryVNodeHoldingIdentity

    private var inputStates: List<String> = emptyList()
    private var referenceStates: List<String> = emptyList()
    private var numOutputStates: Int = 0
    private var timeWindowLowerBound: Instant? = null
    private var timeWindowUpperBound: Instant = defaultTimeWindowUpperBound

    fun setCheckType(uniquenessCheckType: UniquenessCheckRequest.Type): UniquenessCheckRequestBuilder {
        this.uniquenessCheckType = uniquenessCheckType
        return this
    }

    fun setNumOutputStates(numOutputStates: Int): UniquenessCheckRequestBuilder {
        this.numOutputStates = numOutputStates
        return this
    }

    fun setInputStates(inputStates: List<String>): UniquenessCheckRequestBuilder {
        this.inputStates = inputStates.toList()
        return this
    }

    fun setReferenceStates(referenceStates: List<String>): UniquenessCheckRequestBuilder {
        this.referenceStates = referenceStates.toList()
        return this
    }

    fun setTimeWindowLowerBound(timeWindowLowerBound: Instant): UniquenessCheckRequestBuilder {
        this.timeWindowLowerBound = timeWindowLowerBound
        return this
    }

    fun setTimeWindowUpperBound(timeWindowUpperBound: Instant): UniquenessCheckRequestBuilder {
        this.timeWindowUpperBound = timeWindowUpperBound
        return this
    }

    fun setHoldingIdentity(holdingIdentity: UniquenessHoldingIdentity): UniquenessCheckRequestBuilder {
        this.holdingIdentity = holdingIdentity
        return this
    }

    fun build(): UniquenessCheckRequest = UniquenessCheckRequest(
        uniquenessCheckType,
        transactionId,
        initiator,
        inputStates,
        referenceStates,
        numOutputStates,
        timeWindowLowerBound,
        timeWindowUpperBound,
        holdingIdentity
    )
}