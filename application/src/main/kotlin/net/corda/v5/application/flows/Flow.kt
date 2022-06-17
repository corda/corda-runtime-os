package net.corda.v5.application.flows

import net.corda.v5.base.annotations.DoNotImplement

/**
 * Marker interface from which all flow types inherit.
 *
 * This should not be implemented directly when writing a flow. Instead, one of the more specialised types of flow
 * should be implemented:
 * - [RPCStartableFlow] for flows that can be started via RPC
 * - [ResponderFlow] for flows that can be started via a session
 * - [SubFlow] for flows that can be started from another flow
 */
@DoNotImplement
interface Flow