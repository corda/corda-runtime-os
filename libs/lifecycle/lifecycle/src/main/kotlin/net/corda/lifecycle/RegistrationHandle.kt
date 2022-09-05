package net.corda.lifecycle

/**
 * Marker interface representing a registration of a coordinator on some number of child coordinators.
 *
 * A registration is generated whenever a coordinator is instructed to follow a set of child coordinators. The
 * registration inspects any changes to the active state of the child coordinators. When all the child coordinators go
 * active, the parent coordinator receives an event signalling that the registration has gone up. If any of the child
 * coordinators subsequently goes down, another event is sent signalling that this has happened.
 *
 * This is intended to be used to implement domino logic, whereby a component can wait on the status of some number of
 * dependent components to go up before going up itself. This may be particularly important when coping with
 * configuration change.
 *
 * Multiple registrations may be generated per-coordinator. When receiving an event signalling that the child
 * coordinators have all gone up, the event will indicate for which registration this applies.
 *
 * On calling the close method, the registration is removed from the child coordinators. Once close has returned, it is
 * guaranteed that no further events for that registration will be delivered to the coordinator that originally
 * requested the registration.
 */
interface RegistrationHandle : Resource
