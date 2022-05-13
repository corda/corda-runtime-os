package net.corda.libs.packaging.internal

import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.DependencyResolutionException
import net.corda.libs.packaging.VersionComparator
import java.util.Collections
import java.util.NavigableMap
import java.util.NavigableSet
import java.util.TreeSet

internal object CpkDependencyResolver {

    @Suppress("NestedBlockDepth", "ComplexMethod", "ThrowsCount")
    fun resolveDependencies(roots: Iterable<Cpk.Identifier>,
                            availableIds: NavigableMap<Cpk.Identifier, NavigableSet<Cpk.Identifier>>,
                            useSignatures : Boolean): NavigableSet<Cpk.Identifier> {
        val stack = ArrayList<Cpk.Identifier>()
        stack.addAll(roots)
        val resolvedSet: NavigableSet<Cpk.Identifier> = TreeSet()
        val requesterMap = HashMap<String, ArrayList<Cpk.Identifier>>()
        while (stack.isNotEmpty()) {
            val cpkIdentifier = stack.removeAt(stack.size - 1)
            val dependencyAlreadyResolved = resolvedSet.tailSet(cpkIdentifier).any { it.name == cpkIdentifier.name }
            if (!dependencyAlreadyResolved) {
                //All CPKs with the required symbolic name and version greater or equal are valid candidates
                val needle = CpkIdentifierImpl(cpkIdentifier.name, cpkIdentifier.version, null)
                val cpkCandidates = availableIds.tailMap(needle).asSequence()
                        .filter { it.key.name == needle.name && (!useSignatures || cpkIdentifier.signerSummaryHash == it.key.signerSummaryHash) }
                        .toList()
                when {
                    cpkCandidates.isNotEmpty() -> {
                        /** Select the last candidate (with the highest [Cpk.Identifier.version]) */
                        val resolvedCandidate = cpkCandidates.last()
                        val resolvedVersion = resolvedCandidate.key.version
                        if (VersionComparator.cmp(resolvedVersion, cpkIdentifier.version) < 0) {
                            throw DependencyResolutionException("Version '${cpkIdentifier.name}' of CPK " +
                                    "'${cpkIdentifier.name}' was required, but the highest available version is '$resolvedVersion'")
                        }
                        /** Raise an error if there are multiple CPKs with the same name and version that satisfy the signature requirements */
                        val ambiguousCandidates = cpkCandidates.filter { it.key.version == resolvedVersion }
                        if(ambiguousCandidates.size > 1) {
                            throw DependencyResolutionException("CPK $cpkIdentifier, required by ${requesterMap[cpkIdentifier.name]}, " +
                                "is ambiguous as it can be provided by multiple candidates: ${ambiguousCandidates.map { it.key }}")
                        }
                        resolvedSet.add(resolvedCandidate.key)
                        resolvedCandidate.value.forEach { dependency ->
                            requesterMap.computeIfAbsent(dependency.name) { ArrayList() }.add(resolvedCandidate.key)
                            stack.add(dependency)
                        }
                    }
                    else -> throw DependencyResolutionException("CPK $cpkIdentifier, required by ${requesterMap[cpkIdentifier.name]}, was not found")
                }
            }
        }
        return Collections.unmodifiableNavigableSet(resolvedSet)
    }
}