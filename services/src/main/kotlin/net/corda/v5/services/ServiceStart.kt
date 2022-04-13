package net.corda.v5.services

import net.corda.v5.base.annotations.DoNotImplement

/**
 * This event is dispatched once all corda services have been instantiated and registered as injectable services.
 *
 * Any logic required to start the service should be added in response to this event. Services are ordered based on their usages of
 * [CordaInjectPreStart] such that all [CordaInjectPreStart] annotated services have this event distributed to them before the parent
 * service, which annotated the previously mentioned services, receives notification of this event. This event is then distributed to
 * the remaining services which are not using the [CordaInjectPreStart] annotation.
 */
@DoNotImplement
interface ServiceStart : ServiceLifecycleEvent
