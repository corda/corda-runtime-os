package net.corda.packaging.internal

import net.corda.packaging.CPI
import net.corda.packaging.CPK
import net.corda.packaging.VersionComparator
import net.corda.v5.crypto.SecureHash
import java.util.Collections
import java.util.NavigableMap
import java.util.TreeMap
import javax.security.auth.x500.X500Principal


internal data class CPIIdentityImpl(override val name: X500Principal,
                      override val groupId: String) : CPI.Identity {

    private companion object {
        private val comparator : Comparator<CPI.Identity> = Comparator.comparing(CPI.Identity::groupId)
            .thenComparing { identity -> identity.name.name }
    }

    override fun compareTo(other: CPI.Identity) = comparator.compare(this, other)
}

data class CPIIdentifierImpl(
    override val name: String,
    override val version: String,
    override val signerSummaryHash: SecureHash?,
    override val identity: CPI.Identity?
) : CPI.Identifier {

    internal companion object {
        private val identifierComparator = Comparator.comparing(CPI.Identifier::name)
            .thenComparing(CPI.Identifier::version, VersionComparator())
            .thenComparing(CPI.Identifier::signerSummaryHash, secureHashComparator)
    }

    override fun compareTo(other: CPI.Identifier) = identifierComparator.compare(this, other)
}

internal class CPIMetadataImpl(
    override val id: CPI.Identifier,
    cpks: Iterable<CPK.Metadata>,
    override val networkPolicy: String?) : CPI.Metadata {
    private val cpkMap : NavigableMap<CPK.Identifier, CPK.Metadata>

    init {
        cpkMap = cpks.asSequence().map {
            it.id to it
        }.toMap(TreeMap())
    }
    override val cpks: Collection<CPK.Metadata>
        get() = Collections.unmodifiableCollection(cpkMap.values)

    override fun cpkById(id: CPK.Identifier) = cpkMap[id] ?: throw NoSuchElementException(
        "No CPK file with id '$id' exist in this CPI"
    )
}

internal class CPIImpl(override val metadata: CPI.Metadata, cpks : Iterable<CPK>) : CPI {

    private val cpkMap : NavigableMap<CPK.Identifier, CPK>

    override val cpks: Collection<CPK>
    get() = Collections.unmodifiableCollection(cpkMap.values)

    init {
        cpkMap = cpks.asSequence().map {
            it.metadata.id to it
        }.toMap(TreeMap())
    }

    override fun getCPKById(id: CPK.Identifier): CPK? = cpkMap[id]
}