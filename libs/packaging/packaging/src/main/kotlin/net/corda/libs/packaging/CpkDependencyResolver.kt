package net.corda.libs.packaging

import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.comparator.VersionComparator
import net.corda.libs.packaging.core.exception.DependencyResolutionException
import java.util.Collections
import java.util.NavigableMap
import java.util.NavigableSet
import java.util.TreeSet

object CpkDependencyResolver {

    @Suppress("NestedBlockDepth", "ComplexMethod", "ThrowsCount")
    fun resolveDependencies(roots: Iterable<CpkIdentifier>,
                            availableIds: NavigableMap<CpkIdentifier, NavigableSet<CpkIdentifier>>,
                            useSignatures : Boolean): NavigableSet<CpkIdentifier> {
        val stack = ArrayList<CpkIdentifier>()
        stack.addAll(roots)
        val resolvedSet: NavigableSet<CpkIdentifier> = TreeSet()
        val requesterMap = HashMap<String, ArrayList<CpkIdentifier>>()
        while (stack.isNotEmpty()) {
            val cpkIdentifier = stack.removeAt(stack.size - 1)
            val dependencyAlreadyResolved = resolvedSet.tailSet(cpkIdentifier).any { it.name == cpkIdentifier.name }
            if (!dependencyAlreadyResolved) {
                //All CPKs with the required symbolic name and version greater or equal are valid candidates
                val needle = cpkIdentifier
                val cpkCandidates = availableIds.tailMap(needle).asSequence()
                    .filter { it.key.name == needle.name
                            && (!useSignatures || cpkIdentifier.signerSummaryHash == it.key.signerSummaryHash) }
                    .toList()
                when {
                    cpkCandidates.isNotEmpty() -> {
                        /** Select the last candidate (with the highest [CpkIdentifier.version]) */
                        val resolvedCandidate = cpkCandidates.last()
                        val resolvedVersion = resolvedCandidate.key.version
                        if (VersionComparator.cmp(resolvedVersion, cpkIdentifier.version) < 0) {
                            throw DependencyResolutionException(
                                "Version '${cpkIdentifier.name}' of CPK '${cpkIdentifier.name}' was required," +
                                        " but the highest available version is '$resolvedVersion'")
                        }
                        /** Raise an error if there are multiple CPKs with the same name and version that satisfy the
                         *  signature requirements */
                        val ambiguousCandidates = cpkCandidates.filter { it.key.version == resolvedVersion }
                        if(ambiguousCandidates.size > 1) {
                            throw DependencyResolutionException(
                                "CPK $cpkIdentifier, required by ${requesterMap[cpkIdentifier.name]}, " +
                                        "is ambiguous as it can be provided by multiple candidates: ${ambiguousCandidates.map { it.key }}")
                        }
                        resolvedSet.add(resolvedCandidate.key)
                        resolvedCandidate.value.forEach { dependency ->
                            requesterMap.computeIfAbsent(dependency.name) { ArrayList() }.add(resolvedCandidate.key)
                            stack.add(dependency)
                        }
                    }
                    else -> throw DependencyResolutionException(
                        "CPK $cpkIdentifier, required by ${requesterMap[cpkIdentifier.name]}, was not found")
                }
            }
        }
        return Collections.unmodifiableNavigableSet(resolvedSet)
    }
}