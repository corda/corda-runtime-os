package net.corda.sandbox.internal

import net.corda.sandbox.SandboxException
import java.util.UUID

/** Information about the sandbox that can be mapped to and from a unique OSGi bundle location. */
@Suppress("ThrowsCount")
data class SandboxLocation(val securityDomain: String, val id: UUID, val source: String) {
    companion object {
        fun fromString(string: String): SandboxLocation {
            val components = string.split('/', limit = 3)

            if (components.size != 3) {
                throw SandboxException(
                    "Sandbox bundle location had incorrect format: $string. Expected " +
                            "\"{security-domain}/{sandbox-id}/{sandbox-source}\"."
                )
            }

            val sandboxId = try {
                UUID.fromString(components[1])
            } catch (e: IllegalArgumentException) {
                throw SandboxException("Sandbox ID ${components[1]} was not a valid UUID.")
            }
            return SandboxLocation(components[0], sandboxId, components[2])
        }
    }

    override fun toString() = "$securityDomain/$id/$source"
}