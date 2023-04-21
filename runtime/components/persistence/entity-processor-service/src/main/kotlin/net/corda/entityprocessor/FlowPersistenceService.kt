package net.corda.entityprocessor

import net.corda.lifecycle.Lifecycle

/**
 * Component that supports the persistence API for Flow Processors.
 *
 * @constructor Create empty Flow persistence service
 */
interface FlowPersistenceService: Lifecycle