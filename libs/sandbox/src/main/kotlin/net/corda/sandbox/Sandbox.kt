package net.corda.sandbox

import java.util.UUID

/** A container for isolating a set of bundles. */
interface Sandbox {
    // The sandbox's unique identifier.
    val id: UUID
}