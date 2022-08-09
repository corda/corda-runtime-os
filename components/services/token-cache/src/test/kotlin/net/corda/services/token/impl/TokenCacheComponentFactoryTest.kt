package net.corda.services.token.impl

import com.typesafe.config.ConfigFactory
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.services.TokenClaimQuery
import net.corda.data.services.TokenEvent
import net.corda.data.services.TokenSetKey
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig
import net.corda.schema.configuration.ServicesConfig
import net.corda.services.token.TokenCacheComponentFactory
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class TokenCacheComponentFactoryTest {

    @Test
    fun `test`() {
        val factory = TokenCacheComponentFactory(
            mock(),
            mock(),
            mock(),
            mock(),
            mock()
        )

        val component = factory.create()

        val testConfig = mutableMapOf<String, Any>(
            ServicesConfig.TOKEN_CLAIM_TIMEOUT to 500000L,
        )

        val cfg = ConfigFactory.parseMap(testConfig)
        val processor = factory.tokenCacheEventProcessorFactory.create(SmartConfigFactory.create(cfg).create(cfg))
        val key = TokenSetKey().apply {
            this.tokenType = ""
            this.shortHolderId = ""
            this.issuerHash = ""
            this.notaryHash = ""
            this.symbol = ""
        }

        val requestContext = ExternalEventContext().apply {
            this.requestId="r1"
            this.flowId="f1"
        }

        val payload = TokenClaimQuery().apply {
            this.requestContext = requestContext
            this.tokenSet = key
            this.targetAmount = 100
            this.awaitWhenClaimed=false
        }

        val tokenEvent = TokenEvent().apply {
            this.tokenSetKey = key
            this.payload=payload
        }

        val eventRecord = Record("",key, tokenEvent)
        processor.onNext(null, eventRecord)
        println(component)
    }
}