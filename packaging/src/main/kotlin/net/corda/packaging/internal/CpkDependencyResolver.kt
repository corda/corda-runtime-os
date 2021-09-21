package net.corda.packaging.internal

import net.corda.packaging.Cpk
import net.corda.packaging.DependencyResolutionException
import net.corda.packaging.VersionComparator
import net.corda.v5.crypto.SecureHash
import java.util.Collections
import java.util.NavigableMap
import java.util.NavigableSet
import java.util.TreeSet

object CpkDependencyResolver {

    //We need an SecureHash instance that compares lower against with all other
    private val zerothHash = SecureHash("", ByteArray(1))

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
            val dependencyAlreadyResolved = resolvedSet.tailSet(cpkIdentifier).any { it.symbolicName == cpkIdentifier.symbolicName }
            if (!dependencyAlreadyResolved) {
                //All CPKs with the required symbolic name and version greater or equal are valid candidates
                val needle = cpkIdentifier.copy(signerSummaryHash = zerothHash)
                val cpkCandidates = availableIds.tailMap(needle).asSequence()
                        .filter { it.key.symbolicName == needle.symbolicName && (!useSignatures || cpkIdentifier.signerSummaryHash == it.key.signerSummaryHash) }
                        .toList()
                when {
                    cpkCandidates.isNotEmpty() -> {
                        /** Select the last candidate (with the highest [Cpk.Identifier.version]) */
                        val resolvedCandidate = cpkCandidates.last()
                        val resolvedVersion = resolvedCandidate.key.version
                        if (VersionComparator.cmp(resolvedVersion, cpkIdentifier.version) < 0) {
                            throw DependencyResolutionException("Version '${cpkIdentifier.symbolicName}' of CPK " +
                                    "'${cpkIdentifier.symbolicName}' was required, but the highest available version is '$resolvedVersion'")
                        }
                        /** Raise an error if there are multiple CPKs with the same name and version that satisfy the signature requirements */
                        val ambiguousCandidates = cpkCandidates.filter { it.key.version == resolvedVersion }
                        if(ambiguousCandidates.size > 1) {
                            throw DependencyResolutionException("CPK $cpkIdentifier, required by ${requesterMap[cpkIdentifier.symbolicName]}, " +
                                "is ambiguous as it can be provided by multiple candidates: ${ambiguousCandidates.map { it.key }}")
                        }
                        resolvedSet.add(resolvedCandidate.key)
                        resolvedCandidate.value.forEach { dependency ->
                            requesterMap.computeIfAbsent(dependency.symbolicName) { ArrayList() }.add(resolvedCandidate.key)
                            stack.add(dependency)
                        }
                    }
                    else -> throw DependencyResolutionException("Cpk $cpkIdentifier, required by ${requesterMap[cpkIdentifier.symbolicName]}, was not found")
                }
            }
        }
        return Collections.unmodifiableNavigableSet(resolvedSet)
    }
}