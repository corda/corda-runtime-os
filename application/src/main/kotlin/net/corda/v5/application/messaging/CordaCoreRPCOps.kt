@file:Suppress("TooManyFunctions")
package net.corda.v5.application.messaging

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowId
import net.corda.v5.application.identity.AbstractParty
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.application.identity.Party
import net.corda.v5.application.node.MemberInfo
import net.corda.v5.application.node.NetworkParameters
import net.corda.v5.application.node.NodeDiagnosticInfo
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash
import java.security.PublicKey
import java.time.Instant
import java.util.concurrent.CompletableFuture

/**
 * Data class containing information about the scheduled network parameters update. The info is emitted every time node
 * receives network map with [ParametersUpdate] which wasn't seen before. For more information see: [CordaRPCOps.networkParametersFeed]
 * and [CordaRPCOps.acceptNewNetworkParameters].
 * @property hash new [NetworkParameters] hash
 * @property parameters new [NetworkParameters] data structure
 * @property description description of the update
 * @property updateDeadline deadline for accepting this update using [CordaRPCOps.acceptNewNetworkParameters]
 */
@CordaSerializable
data class ParametersUpdateInfo(
        val hash: SecureHash,
        val parameters: NetworkParameters,
        val description: String,
        val updateDeadline: Instant
)

/** RPC operations that the node exposes to clients. */
@Suppress("TooManyFunctions")
interface CordaCoreRPCOps : RPCOps {

    /** Returns the network parameters the node is operating under. */
    val networkParameters: NetworkParameters

    /**
     * Start the given flow with the given arguments. [logicType] must be annotated
     * with [net.corda.v5.application.flows.StartableByRPC].
     */
    @RPCReturnsObservables
    fun <T> startFlowDynamic(logicType: Class<out Flow<T>>, vararg args: Any?): FlowHandle<T>

    /**
     * Start the given flow with the given arguments and a [clientId].
     *
     * The flow's result/ exception will be available for the client to re-connect and retrieve even after the flow's lifetime,
     * by re-calling [startFlowDynamicWithClientId] with the same [clientId]. The [logicType] and [args] will be ignored if the
     * [clientId] matches an existing flow. If you don't have the original values, consider using [reattachFlowWithClientId].
     *
     * Upon calling [removeClientId], the node's resources holding the result/ exception will be freed and the result/ exception will
     * no longer be available.
     *
     * [logicType] must be annotated with [net.corda.v5.application.flows.StartableByRPC].
     *
     * @param clientId The client id to relate the flow to (or is already related to if the flow already exists)
     * @param logicType The [Flow] to start
     * @param args The arguments to pass to the flow
     */
    @RPCReturnsObservables
    fun <T> startFlowDynamicWithClientId(clientId: String, logicType: Class<out Flow<T>>, vararg args: Any?): FlowHandleWithClientId<T>

    /**
     * Attempts to kill a flow. This is not a clean termination and should be reserved for exceptional cases such as stuck fibers.
     *
     * @return whether the flow existed and was killed.
     */
    fun killFlow(id: FlowId): Boolean

    /**
     * Reattach to an existing flow that was started with [startFlowDynamicWithClientId] and has a [clientId].
     *
     * If there is a flow matching the [clientId] then its result or exception is returned.
     *
     * When there is no flow matching the [clientId] then [null] is returned directly (not a future/[FlowHandleWithClientId]).
     *
     * Calling [reattachFlowWithClientId] after [removeClientId] with the same [clientId] will cause the function to return [null] as
     * the result/exception of the flow will no longer be available.
     *
     * @param clientId The client id relating to an existing flow
     */
    @RPCReturnsObservables
    fun <T> reattachFlowWithClientId(clientId: String): FlowHandleWithClientId<T>?

    /**
     * Removes a flow's [clientId] to result/ exception mapping. If the mapping is of a running flow, then the mapping will not get removed.
     * This version will only remove flow's that were started by the same user currently calling [removeClientId].
     *
     * See [startFlowDynamicWithClientId] for more information.
     *
     * @return whether the mapping was removed.
     */
    fun removeClientId(clientId: String): Boolean

    /**
     * Removes a flow's [clientId] to result/ exception mapping. If the mapping is of a running flow, then the mapping will not get removed.
     * This version can be called for all client ids, ignoring which user originally started a flow with [clientId].
     *
     * See [startFlowDynamicWithClientId] for more information.
     *
     * @return whether the mapping was removed.
     */
    fun removeClientIdAsAdmin(clientId: String): Boolean

    /**
     * Returns all finished flows that were started with a client ID for which the client ID mapping has not been removed. This version only
     * returns the client ids for flows started by the same user currently calling [finishedFlowsWithClientIds].
     *
     * @return A [Map] containing client ids for finished flows started by the user calling [finishedFlowsWithClientIds], mapped to [true]
     * if finished successfully, [false] if completed exceptionally.
     */
    fun finishedFlowsWithClientIds(): Map<String, Boolean>

    /**
     * Returns all finished flows that were started with a client id by all RPC users for which the client ID mapping has not been removed.
     *
     * @return A [Map] containing all client ids for finished flows, mapped to [true] if finished successfully,
     * [false] if completed exceptionally.
     */
    fun finishedFlowsWithClientIdsAsAdmin(): Map<String, Boolean>

    /** Returns Node's MemberInfo, assuming this will not change while the node is running. */
    fun memberInfo(): MemberInfo

    /**
     * Returns Node's NodeDiagnosticInfo, including the version details as well as the information about installed CorDapps.
     */
    fun nodeDiagnosticInfo(): NodeDiagnosticInfo

    /** Returns the node's current time. */
    fun currentNodeTime(): Instant

    /**
     * Returns a [CompletableFuture] which completes when the node has registered with the Member Lookup service. It can also
     * complete with an exception if it is unable to.
     */
    @RPCReturnsObservables
    fun waitUntilNetworkReady(): CompletableFuture<Void?>

    // TODO These need rethinking. Instead of these direct calls we should have a way of replicating a subset of
    // the node's state locally and query that directly.
    /**
     * Returns the well known identity from an abstract party. This is intended to resolve the well known identity
     * from a confidential identity, however it transparently handles returning the well known identity back if
     * a well known identity is passed in.
     *
     * @param party identity to determine well known identity for.
     * @return well known identity, if found.
     */
    fun partyFromAnonymous(party: AbstractParty): Party?

    /** Returns the [Party] corresponding to the given key, if found. */
    fun partyFromKey(key: PublicKey): Party?

    /** Returns the [Party] with the X.500 principal as it's [Party.name]. */
    fun partyFromName(x500Name: CordaX500Name): Party?

    /**
     * Returns a list of candidate matches for a given string, with optional fuzzy(ish) matching. Fuzzy matching may
     * get smarter with time e.g. to correct spelling errors, so you should not hard-code indexes into the results
     * but rather show them via a user interface and let the user pick the one they wanted.
     *
     * @param query The string to check against the X.500 name components
     * @param exactMatch If true, a case sensitive match is done against each component of each X.500 name.
     */
    fun partiesFromName(query: String, exactMatch: Boolean): Set<Party>

    /** Enumerates the class names of the flows that this node knows about. */
    fun registeredFlows(): List<String>

    /** Sets the value of the node's flows draining mode.
     * If this mode is [enabled], the node will reject new flows through RPC, ignore scheduled flows, and do not process
     * initial session messages, meaning that P2P counterparties will not be able to initiate new flows involving the node.
     *
     * @param enabled whether the flows draining mode will be enabled.
     * */
    fun setFlowsDrainingModeEnabled(enabled: Boolean)

    /**
     * Returns whether the flows draining mode is enabled.
     *
     * @see setFlowsDrainingModeEnabled
     */
    fun isFlowsDrainingModeEnabled(): Boolean

    /**
     * Shuts the node down. Returns immediately.
     * This does not wait for flows to be completed.
     */
    fun shutdown()

    /**
     * Shuts the node down. Returns immediately.
     * @param drainPendingFlows whether the node will wait for pending flows to be completed before exiting. While draining, new flows from RPC will be rejected.
     */
    fun terminate(drainPendingFlows: Boolean = false)

    /**
     * Returns whether the node is waiting for pending flows to complete before shutting down.
     * Disabling draining mode cancels this state.
     *
     * @return whether the node will shutdown when the pending flows count reaches zero.
     */
    fun isWaitingForShutdown(): Boolean

}

// Note that the passed in constructor function is only used for unification of other type parameters and reification of
// the Class instance of the flow. This could be changed to use the constructor function directly.

inline fun <T, reified R : Flow<T>> CordaCoreRPCOps.startFlow(
        @Suppress("UNUSED_PARAMETER")
        flowConstructor: () -> R
): FlowHandle<T> = startFlowDynamic(R::class.java)

inline fun <T, A, reified R : Flow<T>> CordaCoreRPCOps.startFlow(
        @Suppress("UNUSED_PARAMETER")
        flowConstructor: (A) -> R,
        arg0: A
): FlowHandle<T> = startFlowDynamic(R::class.java, arg0)

/**
 * Extension function for type safe invocation of flows from Kotlin, for example:
 *
 * val rpc: CordaCoreRPCOps = (..)
 * rpc.startFlow(::ResolveTransactionsFlow, setOf<SecureHash>(), aliceIdentity)
 */
inline fun <T, A, B, reified R : Flow<T>> CordaCoreRPCOps.startFlow(
        @Suppress("UNUSED_PARAMETER")
        flowConstructor: (A, B) -> R,
        arg0: A,
        arg1: B
): FlowHandle<T> = startFlowDynamic(R::class.java, arg0, arg1)

inline fun <T, A, B, C, reified R : Flow<T>> CordaCoreRPCOps.startFlow(
        @Suppress("UNUSED_PARAMETER")
        flowConstructor: (A, B, C) -> R,
        arg0: A,
        arg1: B,
        arg2: C
): FlowHandle<T> = startFlowDynamic(R::class.java, arg0, arg1, arg2)

inline fun <T, A, B, C, D, reified R : Flow<T>> CordaCoreRPCOps.startFlow(
        @Suppress("UNUSED_PARAMETER")
        flowConstructor: (A, B, C, D) -> R,
        arg0: A,
        arg1: B,
        arg2: C,
        arg3: D
): FlowHandle<T> = startFlowDynamic(R::class.java, arg0, arg1, arg2, arg3)

@Suppress("LongParameterList")
inline fun <T, A, B, C, D, E, reified R : Flow<T>> CordaCoreRPCOps.startFlow(
        @Suppress("UNUSED_PARAMETER")
        flowConstructor: (A, B, C, D, E) -> R,
        arg0: A,
        arg1: B,
        arg2: C,
        arg3: D,
        arg4: E
): FlowHandle<T> = startFlowDynamic(R::class.java, arg0, arg1, arg2, arg3, arg4)

@Suppress("LongParameterList")
inline fun <T, A, B, C, D, E, F, reified R : Flow<T>> CordaCoreRPCOps.startFlow(
        @Suppress("UNUSED_PARAMETER")
        flowConstructor: (A, B, C, D, E, F) -> R,
        arg0: A,
        arg1: B,
        arg2: C,
        arg3: D,
        arg4: E,
        arg5: F
): FlowHandle<T> = startFlowDynamic(R::class.java, arg0, arg1, arg2, arg3, arg4, arg5)

/**
 * Extension function for type safe invocation of flows from Kotlin, with [clientId].
 */
@Suppress("unused")
inline fun <T, reified R : Flow<T>> CordaCoreRPCOps.startFlowWithClientId(
        clientId: String,
        @Suppress("unused_parameter")
        flowConstructor: () -> R
): FlowHandleWithClientId<T> = startFlowDynamicWithClientId(clientId, R::class.java)

@Suppress("unused")
inline fun <T, A, reified R : Flow<T>> CordaCoreRPCOps.startFlowWithClientId(
        clientId: String,
        @Suppress("unused_parameter")
        flowConstructor: (A) -> R,
        arg0: A
): FlowHandleWithClientId<T> = startFlowDynamicWithClientId(clientId, R::class.java, arg0)

@Suppress("unused")
inline fun <T, A, B, reified R : Flow<T>> CordaCoreRPCOps.startFlowWithClientId(
        clientId: String,
        @Suppress("unused_parameter")
        flowConstructor: (A, B) -> R,
        arg0: A,
        arg1: B
): FlowHandleWithClientId<T> = startFlowDynamicWithClientId(clientId, R::class.java, arg0, arg1)

@Suppress("unused")
inline fun <T, A, B, C, reified R : Flow<T>> CordaCoreRPCOps.startFlowWithClientId(
        clientId: String,
        @Suppress("unused_parameter")
        flowConstructor: (A, B, C) -> R,
        arg0: A,
        arg1: B,
        arg2: C
): FlowHandleWithClientId<T> = startFlowDynamicWithClientId(clientId, R::class.java, arg0, arg1, arg2)

@Suppress("unused", "LongParameterList")
inline fun <T, A, B, C, D, reified R : Flow<T>> CordaCoreRPCOps.startFlowWithClientId(
        clientId: String,
        @Suppress("unused_parameter")
        flowConstructor: (A, B, C, D) -> R,
        arg0: A,
        arg1: B,
        arg2: C,
        arg3: D
): FlowHandleWithClientId<T> = startFlowDynamicWithClientId(clientId, R::class.java, arg0, arg1, arg2, arg3)

@Suppress("unused", "LongParameterList")
inline fun <T, A, B, C, D, E, reified R : Flow<T>> CordaCoreRPCOps.startFlowWithClientId(
        clientId: String,
        @Suppress("unused_parameter")
        flowConstructor: (A, B, C, D, E) -> R,
        arg0: A,
        arg1: B,
        arg2: C,
        arg3: D,
        arg4: E
): FlowHandleWithClientId<T> = startFlowDynamicWithClientId(clientId, R::class.java, arg0, arg1, arg2, arg3, arg4)

@Suppress("unused", "LongParameterList")
inline fun <T, A, B, C, D, E, F, reified R : Flow<T>> CordaCoreRPCOps.startFlowWithClientId(
        clientId: String,
        @Suppress("unused_parameter")
        flowConstructor: (A, B, C, D, E, F) -> R,
        arg0: A,
        arg1: B,
        arg2: C,
        arg3: D,
        arg4: E,
        arg5: F
): FlowHandleWithClientId<T> = startFlowDynamicWithClientId(clientId, R::class.java, arg0, arg1, arg2, arg3, arg4, arg5)

