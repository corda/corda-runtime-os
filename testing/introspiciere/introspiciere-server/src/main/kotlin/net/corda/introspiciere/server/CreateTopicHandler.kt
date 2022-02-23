package net.corda.introspiciere.server

import io.javalin.http.Context
import io.javalin.http.Handler
import net.corda.introspiciere.core.CreateTopicUseCase
import net.corda.introspiciere.core.UseCase
import net.corda.introspiciere.domain.TopicDefinitionPayload

internal class CreateTopicHandler(private val createTopicUseCase: UseCase<CreateTopicUseCase.Input>) : Handler {
    override fun handle(ctx: Context) {
        wrapException {
            val topic = ctx.pathParam("topic")
            val definition = ctx.bodyAsClass<TopicDefinitionPayload>()

            createTopicUseCase.execute(CreateTopicUseCase.Input(
                topic, definition.partitions, definition.replicationFactor, definition.config
            ))

            ctx.result("OK")
        }
    }
}