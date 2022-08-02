package net.corda.ledger.consensual

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.MerkleTreeFactoryImpl
import net.corda.flow.application.crypto.SigningServiceImpl
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowFiberServiceImpl
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class ConsensualLedgerServiceImplTest {
    companion object {
        private lateinit var digestService: DigestService
        private lateinit var merkleTreeFactory: MerkleTreeFactory
        private lateinit var signingService: SigningService
        private lateinit var flowFiberService: FlowFiberService
        private lateinit var schemeMetadata: CipherSchemeMetadata

        @BeforeAll
        @JvmStatic
        fun setup() {
            schemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            merkleTreeFactory = MerkleTreeFactoryImpl(digestService)
            flowFiberService = FlowFiberServiceImpl()
            signingService = SigningServiceImpl(flowFiberService, schemeMetadata)
        }
    }

    /**
    Throws this:
    co/paralleluniverse/fibers/suspend/SuspendExecution
    java.lang.NoClassDefFoundError: co/paralleluniverse/fibers/suspend/SuspendExecution
    at net.corda.flow.fiber.FlowFiberServiceImpl.getExecutingFiber(FlowFiberServiceImpl.kt:11)
    at net.corda.ledger.consensual.ConsensualLedgerServiceImpl.getTransactionBuilder(ConsensualLedgerServiceImpl.kt:30)
    at net.corda.ledger.consensual.ConsensualLedgerServiceImplTest.Test basic behaviour(ConsensualLedgerServiceImplTest.kt:40)
    at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
    at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
    at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
    at java.base/java.lang.reflect.Method.invoke(Method.java:566)
     */
    @Test
    fun `getTransactionBuilder should return a Transaction Builder`() {
        val service = ConsensualLedgerServiceImpl(merkleTreeFactory, digestService, signingService, flowFiberService, schemeMetadata)
        val transactionBuilder = service.getTransactionBuilder()
        assertIs<ConsensualTransactionBuilder>(transactionBuilder)
    }
}
