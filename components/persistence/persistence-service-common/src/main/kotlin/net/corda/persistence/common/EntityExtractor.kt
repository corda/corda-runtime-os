package net.corda.persistence.common

import net.corda.libs.packaging.core.CpkMetadata

object EntityExtractor {
    /** Extract the set of class names (that were annotated with @Entity and @CordaSerializable) */
    fun getEntityClassNames(cpksMetadata: Iterable<CpkMetadata>): Set<String> =
        cpksMetadata.flatMapTo(LinkedHashSet()) { it.cordappManifest.entities }
}
