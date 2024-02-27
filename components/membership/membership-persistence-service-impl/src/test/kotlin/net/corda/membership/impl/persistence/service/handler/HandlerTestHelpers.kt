package net.corda.membership.impl.persistence.service.handler

import io.micrometer.core.instrument.Timer
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import java.util.concurrent.Callable

val transactionTimer: Timer = mock {
    on { recordCallable(any<Callable<*>>()) } doAnswer {
        @Suppress("unchecked_cast")
        (it.arguments[0] as Callable<Any>).call()
    }
}
