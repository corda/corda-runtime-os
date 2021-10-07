package net.corda.cpi.read

import com.typesafe.config.Config
import net.corda.packaging.Cpb

fun interface CPIMetadataListener {

    fun onUpdate(changedKeys: Set<String>, currentSnapshot: Map<String, Cpb.MetaData>)

}