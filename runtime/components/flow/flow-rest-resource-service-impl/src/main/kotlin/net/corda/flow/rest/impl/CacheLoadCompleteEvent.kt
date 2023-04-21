package net.corda.flow.rest.impl

import net.corda.lifecycle.LifecycleEvent

/**
 * This event is used to signal the flow status cache has completed a snapshot load.
 */
class CacheLoadCompleteEvent : LifecycleEvent