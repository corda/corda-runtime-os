package net.corda.utilities.clock.impl

import net.corda.utilities.clock.ClockFactory
import net.corda.utilities.time.UTCClock
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component(
    service = [ClockFactory::class]
)
class ClockFactoryImpl @Activate constructor() : ClockFactory {
    override fun createUTCClock(): net.corda.utilities.time.Clock = UTCClock()
}
