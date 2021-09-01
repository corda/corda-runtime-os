package net.corda.kryoserialization

import net.corda.classinfo.ClassInfoService
import net.corda.sandbox.SandboxGroup
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.serialization.SerializationEncoding

@DoNotImplement
interface CheckpointSerializationContext {
    /**
     * If non-null, apply this encoding (typically compression) when serializing.
     */
    val encoding: SerializationEncoding?

    /**
     * The class loader to use for deserialization (for classes not in a bundle).
     */
    val deserializationClassLoader: ClassLoader

    /**
     * A map of any addition properties specific to the particular use case.
     */
    val properties: Map<Any, Any>

    /**
     * Duplicate references to the same object preserved in the wire format and when deserialized when this is true,
     * otherwise they appear as new copies of the object.
     */
    val objectReferencesEnabled: Boolean

    /**
     * Service used to retrieve information about CPKs from the context of the current sandbox.
     */
    val classInfoService: ClassInfoService

    /**
     * The set of CorDapp sandboxes for the node's CPI.
     *
     * In the future, we will allow multiple CPIs per node, and thus we will need to support multiple sandbox groups.
     */
    val sandboxGroup: SandboxGroup
}
