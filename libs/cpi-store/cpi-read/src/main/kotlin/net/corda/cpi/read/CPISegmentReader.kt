package net.corda.cpi.read

import net.corda.lifecycle.Lifecycle
import net.corda.packaging.CPI
import java.nio.ByteBuffer

interface CPISegmentReader: Lifecycle {
    fun getCPISegment(cpiIdentifier: CPI.Identifier, start: Long, byteBuffer: ByteBuffer): Boolean
}