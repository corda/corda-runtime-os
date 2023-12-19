package net.corda.p2p.crypto.protocol.api

import org.apache.avro.specific.SpecificRecordBase

sealed interface SerialisableSessionData {
    fun toAvro(): SpecificRecordBase
}
