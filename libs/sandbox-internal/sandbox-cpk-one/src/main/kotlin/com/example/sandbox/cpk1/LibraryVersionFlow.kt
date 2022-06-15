package com.example.sandbox.cpk1

import net.corda.v5.application.flows.SubFlow
import org.joda.time.Chronology
import org.osgi.service.component.annotations.Component

/** Retrieves the codesource of this sandbox's [Chronology] class. */
@Suppress("unused")
@Component(name = "library.version.flow")
class LibraryVersionFlow: SubFlow<String> {
    override fun call() = Chronology::class.java.protectionDomain.codeSource.toString()
}