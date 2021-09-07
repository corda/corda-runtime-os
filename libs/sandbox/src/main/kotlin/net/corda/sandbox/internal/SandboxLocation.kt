package net.corda.sandbox.internal

import net.corda.sandbox.SandboxException
import java.net.URI
import java.util.UUID

/** Information about the sandbox that can be mapped to and from a unique OSGi bundle location. */
@Suppress("ThrowsCount")
data class SandboxLocation(val id: UUID, val uri: URI) {
    companion object {
        fun fromString(string: String): SandboxLocation {
            val components = string.split('/')

            if (components.size != 3) {
                throw SandboxException(
                    "Sandbox bundle location had incorrect format: $string. Expected " +
                            "\"sandbox/{sandbox-id}/{sandbox-source-uri}\"."
                )
            }

            if (components[0] != "sandbox") throw SandboxException(
                "Sandbox bundle location had incorrect format: $string. Expected first component to be \"sandbox\"."
            )

            val sandboxId = try {
                UUID.fromString(components[1])
            } catch (e: IllegalArgumentException) {
                throw SandboxException("Sandbox ID ${components[1]} was not a valid UUID.")
            }

            val sandboxUri = try {
                URI.create(components[2])
            } catch (e: IllegalArgumentException) {
                throw SandboxException("Sandbox URI ${components[2]} was not a valid URI.")
            }

            return SandboxLocation(sandboxId, sandboxUri)
        }
    }

    override fun toString() = "sandbox/$id/$uri"
}