package net.corda.p2p.crypto.protocol.api

import org.apache.avro.specific.SpecificRecordBase

sealed interface SessionData {
    fun toAvro(): SpecificRecordBase
}
