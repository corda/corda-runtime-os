package net.corda.cpi.read.factory

import net.corda.cpi.read.CPIRead
import net.corda.cpi.read.CPISegmentReader
import net.corda.libs.configuration.SmartConfig

interface CPIReadFactory {
    fun createCPIRead(nodeConfig: SmartConfig): CPIRead
    fun createCPIReadSegment(nodeConfig: SmartConfig): CPISegmentReader
}

