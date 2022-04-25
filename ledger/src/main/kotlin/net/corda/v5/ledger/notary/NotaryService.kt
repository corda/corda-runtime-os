package net.corda.v5.ledger.notary

import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.ledger.crypto.TransactionDigestAlgorithmNamesFactory
import net.corda.v5.serialization.SingletonSerializeAsToken
import java.security.PublicKey

abstract class NotaryService : SingletonSerializeAsToken {
    abstract val notaryIdentityKey: PublicKey

    @CordaInject
    lateinit var memberLookup: MemberLookup
    @CordaInject
    lateinit var signingService: SigningService
    @CordaInject
    lateinit var transactionDigestAlgorithmNamesFactory: TransactionDigestAlgorithmNamesFactory

    /**
     * Interfaces for the request and result formats of queries supported by notary services. To
     * implement a new query, you must:
     *
     * - Define data classes which implement the [Query.Request] and [Query.Result] interfaces
     * - Add corresponding handling for the new classes within the notary service implementations
     *   that you want to support the query.
     */
    interface Query {
        interface Request
        interface Result
    }

    abstract fun start()
    abstract fun stop()

    /**
     * Produces a notary service flow which has the corresponding sends and receives as [NotaryClientFlow][net.corda.systemflows.NotaryClientFlow].
     * @param otherPartySession client [Party] making the request
     */
    abstract fun createServiceFlow(otherPartySession: FlowSession): Flow<Void?>

    /**
     * Processes a [Query.Request] and returns a [Query.Result].
     *
     * Note that this always throws an [UnsupportedOperationException] to handle notary
     * implementations that do not support this functionality. This must be overridden by
     * notary implementations wishing to support query functionality.
     *
     * Overrides of this function may themselves still throw an [UnsupportedOperationException],
     * if they do not support specific query implementations
     */
    open fun processQuery(query: Query.Request): Query.Result {
        throw UnsupportedOperationException("Notary has not implemented query support")
    }
}