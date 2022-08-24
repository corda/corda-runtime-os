package net.corda.ledger.consensual.impl

import net.corda.crypto.impl.serialization.PublicKeySerializer
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.internal.serialization.amqp.helper.TestFlowFiberServiceWithSerialization
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.test.common.annotation.InjectService

@Component(service = [FlowFiberService::class, SingletonSerializeAsToken::class])
class TestFlowFiberServiceWithSerializationProxy @Activate constructor(
    @Reference(service = CipherSchemeMetadata::class) private val schemeMetadata: CipherSchemeMetadata
) : FlowFiberService, SingletonSerializeAsToken{
    override fun getExecutingFiber(): FlowFiber {
        val testFlowFiberServiceWithSerialization = TestFlowFiberServiceWithSerialization()
        testFlowFiberServiceWithSerialization.configureSerializer ({
        /* Not visible for some reasons. Since serialization is not used in the current tests, not a problem.*/
//            it.register(PartySerializer(), it)
        }, schemeMetadata)
        return testFlowFiberServiceWithSerialization.getExecutingFiber()
    }
}

@TestInstance(PER_CLASS)
@Suppress("FunctionName")
class ConsensualLedgerServiceTest {

    @InjectService(timeout = 1000)
    lateinit var consensualLedgerService: ConsensualLedgerService

    @Test
    fun `getTransactionBuilder should return a Transaction Builder`() {
        val transactionBuilder = consensualLedgerService.getTransactionBuilder()
        assertThat(transactionBuilder).isInstanceOf(ConsensualTransactionBuilder::class.java)
    }
}
