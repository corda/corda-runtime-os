package net.corda.libs.packaging.internal

import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.VersionComparator
import net.corda.v5.crypto.SecureHash
import java.util.Collections
import java.util.NavigableMap
import java.util.TreeMap

internal data class CpiIdentifierImpl(
    override val name: String,
    override val version: String,
    override val signerSummaryHash: SecureHash?
) : Cpi.Identifier {

    internal companion object {
        private val identifierComparator = Comparator.comparing(Cpi.Identifier::name)
            .thenComparing(Cpi.Identifier::version, VersionComparator())
            .thenComparing(Cpi.Identifier::signerSummaryHash, secureHashComparator)
    }

    override fun compareTo(other: Cpi.Identifier) = identifierComparator.compare(this, other)
}

internal class CpiMetadataImpl(
    override val id: Cpi.Identifier,
    override val hash : SecureHash,
    cpks: Iterable<Cpk.Metadata>,
    override val groupPolicy: String?) : Cpi.Metadata {
    private val cpkMap : NavigableMap<Cpk.Identifier, Cpk.Metadata>

    init {
        cpkMap = cpks.asSequence().map {
            it.id to it
        }.toMap(TreeMap())
    }
    override val cpks: Collection<Cpk.Metadata>
        get() = Collections.unmodifiableCollection(cpkMap.values)

    override fun cpkById(id: Cpk.Identifier) = cpkMap[id] ?: throw NoSuchElementException(
        "No CPK file with id '$id' exist in this CPI"
    )

    internal companion object {
        // This comparator is incomplete...
        private val comparator = Comparator.comparing(Cpi.Metadata::id)
            .thenComparing(Cpi.Metadata::hash, secureHashComparator)
    }

    override fun equals(other: Any?): Boolean {
        return other is Cpi.Metadata && comparator.compare(this, other) == 0
    }
}

internal class CpiImpl(override val metadata: Cpi.Metadata, cpks : Iterable<Cpk>) : Cpi {

    private val cpkMap : NavigableMap<Cpk.Identifier, Cpk>

    override val cpks: Collection<Cpk>
    get() = Collections.unmodifiableCollection(cpkMap.values)

    init {
        cpkMap = cpks.asSequence().map {
            it.metadata.id to it
        }.toMap(TreeMap())
    }

    override fun getCpkById(id: Cpk.Identifier): Cpk? = cpkMap[id]

    override fun close() = cpks.forEach(Cpk::close)
}
