package net.corda.libs.packaging.internal

import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkIdentifier
import java.util.Collections
import java.util.TreeMap

internal class CpiImpl(override val metadata: CpiMetadata, cpks : Iterable<Cpk>) : Cpi {

    private val cpkMap : Map<CpkIdentifier, Cpk>

    override val cpks: Collection<Cpk>
    get() = Collections.unmodifiableCollection(cpkMap.values)

    init {
        cpkMap = cpks.asSequence().map {
            it.metadata.cpkId to it
        }.toMap(TreeMap())
    }

    override fun getCpkById(id: CpkIdentifier): Cpk? = cpkMap[id]

    override fun close() = cpks.forEach(Cpk::close)
}
