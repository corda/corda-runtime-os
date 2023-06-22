package net.corda.permissions.management.service

import net.corda.libs.interop.endpoints.v1.InteropManager
import net.corda.lifecycle.Lifecycle

/**
 * Service for managing interop in the system.
 *
 * The service exposes the following APIs:
 * - InteropManager - API for managing interop.
 *
 * To use the Interop Management Service, dependency inject the service using OSGI and start the service. The service will start all
 * necessary interop related dependencies and the above APIs can be used to interact with the system.
 *
 */
interface InteropManagementService : Lifecycle {

    val interopManager: InteropManager
}