package net.corda.cpi.read.factory

import com.typesafe.config.Config
import net.corda.cpi.read.CPIRead
import net.corda.cpi.read.CPISegmentReader

interface CPIReadFactory {
    fun createCPIRead(nodeConfig: Config): CPIRead
    fun createCPIReadSegment(nodeConfig: Config): CPISegmentReader
}

