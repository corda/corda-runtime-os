package net.corda.v5.application.services

import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.application.node.NetworkParameters
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.crypto.SecureHash

/**
 * Service for retrieving network parameters used for resolving transactions according to parameters that were historically in force in the
 * network.
 */
@DoNotImplement
interface NetworkParametersService : CordaServiceInjectable, CordaFlowInjectable {

    /**
     * [SecureHash] of the current parameters for the network.
     *
     * @return The [SecureHash] of the current parameters for the network.
     */
    val currentHash: SecureHash

    /**
     * For backwards compatibility, this parameters hash will be used for resolving historical transactions in the chain.
     *
     * @return The default hash.
     */
    val defaultHash: SecureHash

    /**
     * Return the network parameters with the given [hash], or null if it doesn't exist.
     *
     * @return The [NetworkParameters] that matches the input [hash], or null if it doesn't exist.
     */
    fun lookup(hash: SecureHash): NetworkParameters?
}