package com.example.sandbox.cpk1

import net.corda.v5.application.flows.Flow
import org.osgi.service.component.annotations.Component
import org.joda.time.Chronology

/** Invokes methods on an implementation class from a non-exported package of another bundle. */
@Suppress("unused")
@Component(name = "library.version.flow")
class LibraryVersionFlow: Flow<String> {
    override fun call() = Chronology::class.java.protectionDomain.codeSource.toString()
}