package com.example.sandbox.library

import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope

/** A singleton service defined in a library. Each CPK should get its own copy. */
@Component(service = [LibrarySingletonService::class], scope = ServiceScope.SINGLETON)
class LibrarySingletonService {
    var counter = 0
}