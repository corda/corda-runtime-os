package com.example.sandbox.cpk2

import com.example.sandbox.library.LibrarySingletonService
import net.corda.v5.application.flows.Flow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** Returns the value of the [LibrarySingletonService]'s counter and increments the counter. */
@Suppress("unused")
@Component(name = "library.singleton.user.two")
class LibrarySingletonServiceHolderFlow @Activate constructor(
    @Reference
    private val librarySingletonService: LibrarySingletonService
): Flow<Int> {
    override fun call() = ++librarySingletonService.counter
}