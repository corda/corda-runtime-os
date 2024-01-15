package net.corda.flow.fiber

import net.corda.flow.fiber.cache.FlowFiberCache
import java.util.concurrent.Future

class FiberFuture(
    val interruptable: Interruptable,
    val flowFiberCache: FlowFiberCache,
    val future: Future<FlowIORequest<*>>
)
