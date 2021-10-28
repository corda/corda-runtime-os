package net.corda.cpi.read

import net.corda.lifecycle.Lifecycle
import net.corda.packaging.CPI
import java.nio.ByteBuffer

/**
 * Some CPI store read implementations support retrieving CPI byte segments.
 */
interface CPISegmentReader: Lifecycle {

    /**
     * Retrieve a segment of a CPI as a byte buffer.
     *
     * @param cpiIdentifier Identifies the CPI to read.
     * @param start The start position to read from.
     * @param byteBuffer Bytes read from the CPI are inserted into this byte buffer. The byteBuffer itself can
     * be used to determine how many bytes have been read.
     */
    fun getCPISegment(cpiIdentifier: CPI.Identifier, start: Long, byteBuffer: ByteBuffer): Boolean
}