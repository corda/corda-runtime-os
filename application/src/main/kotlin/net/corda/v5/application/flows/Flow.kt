package net.corda.v5.application.flows

import net.corda.v5.base.annotations.DoNotImplement

/**
 * Marker interface from which all flow types inherit.
 *
 * This should not be implemented directly when writing a flow. Instead, one of the more specialised types of flow
 * should be implemented:
 *
 * - [RestStartableFlow] for flows that are started via RPC.
 * - [ResponderFlow] for flows that are started by a peer session.
 * - [SubFlow] for flows that are started from within other flows.
 */
@DoNotImplement
interface Flow