package net.corda.entityprocessor.impl.internal

import net.corda.libs.packaging.CpkMetadata

object EntityExtractor {
    /** Extract the set of class names (that were annotated with @Entity */
    fun getEntityClassNames(cpksMetadata: Collection<CpkMetadata>): Collection<String> =
        cpksMetadata.flatMap { it.cordappManifest.entities }.toSet()
}
