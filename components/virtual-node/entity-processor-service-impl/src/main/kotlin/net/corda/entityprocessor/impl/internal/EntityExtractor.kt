package net.corda.entityprocessor.impl.internal

import net.corda.libs.packaging.core.CpkMetadata

object EntityExtractor {
    /** Extract the set of class names (that were annotated with @Entity and @CordaSerializable) */
    fun getEntityClassNames(cpksMetadata: Collection<CpkMetadata>): Collection<String> =
        cpksMetadata.flatMap { it.cordappManifest.entities }.toSet()
}
