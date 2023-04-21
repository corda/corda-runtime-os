package net.corda.flow.fiber

import java.util.concurrent.Future

class FiberFuture(
    val interruptable: Interruptable,
    val future: Future<FlowIORequest<*>>
)
