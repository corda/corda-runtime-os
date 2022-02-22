package net.corda.cpk.write.impl.services.db

import net.corda.cpk.write.impl.CpkChecksumData
import java.util.stream.Stream

// Maybe should be promoted to libs
interface CpkStorage   {
    fun getAllCpkInfo(): Stream<CpkChecksumData>
}