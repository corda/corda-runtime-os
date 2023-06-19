package net.corda.libs.interop.endpoints.v1

import net.corda.lifecycle.Lifecycle

/**
 * The [InteropManager] provides functionality for interop management and has a lifecycle.
 */
interface InteropManager : InteropIdentityManager, Lifecycle