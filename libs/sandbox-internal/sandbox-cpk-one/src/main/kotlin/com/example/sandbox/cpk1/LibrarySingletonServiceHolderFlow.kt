package com.example.sandbox.cpk1

import com.example.sandbox.library.LibrarySingletonService
import net.corda.v5.application.flows.SubFlow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** Returns the value of the [LibrarySingletonService]'s counter and increments the counter. */
@Suppress("unused")
@Component(name = "library.singleton.user.one")
class LibrarySingletonServiceHolderFlow @Activate constructor(
    @Reference
    private val librarySingletonService: LibrarySingletonService
): SubFlow<Int> {
    override fun call() = ++librarySingletonService.counter
}