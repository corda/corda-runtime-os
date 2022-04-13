package net.corda.v5.ledger.identity

import net.corda.v5.base.annotations.DoNotImplement

/**
 * The [AnonymousParty] class contains enough information to uniquely identify a [Party] while excluding private
 * information such as name. It is intended to represent a party on the distributed ledger.
 *
 * ### Flow sessions
 *
 * Anonymous parties can be used to communicate using the [FlowMessaging.initiateFlow] method. Message routing is simply routing to the well-known
 * [Party] the anonymous party belongs to. This mechanism assumes the party initiating the communication knows who the anonymous party is.
 */
@DoNotImplement
interface AnonymousParty : AbstractParty