package net.corda.utilities.time.impl

import net.corda.utilities.time.ClockFactory
import net.corda.utilities.time.UTCClock
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Suppress("unused")
@Component(
    service = [ClockFactory::class]
)
class ClockFactoryImpl @Activate constructor() : ClockFactory {
    override fun createUTCClock(): net.corda.utilities.time.Clock = UTCClock()
}
