package net.corda.crypto.hes.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.AbstractComponentNotReadyException
import net.corda.crypto.hes.StableKeyPairDecryptor
import net.corda.lifecycle.test.impl.LifecycleTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

class StableKeyPairDecryptorImplLifecycleTests {
    private lateinit var stableKeyPairDecryptor: StableKeyPairDecryptorImpl

    private val lifecycleTest = LifecycleTest {
        addDependency<CryptoOpsClient>()
        stableKeyPairDecryptor = StableKeyPairDecryptorImpl(
            coordinatorFactory,
            CipherSchemeMetadataImpl(),
            mock(),
        )

        stableKeyPairDecryptor.lifecycleCoordinator
    }

    @Test
    fun `On CryptoOpsClient going UP, StableKeyPairDecryptor creates Impl and goes UP`() {
        lifecycleTest.run {
            testClass.start()
            bringDependencyUp<CryptoOpsClient>()
            assertNotNull(stableKeyPairDecryptor.impl)
            verifyIsUp<StableKeyPairDecryptor>()
        }
    }

    @Test
    fun `On CryptoOpsClient going DOWN, StableKeyPairDecryptor goes DOWN`() {
        lifecycleTest.run {
            testClass.start()
            bringDependencyUp<CryptoOpsClient>()
            verifyIsUp<StableKeyPairDecryptor>()

            bringDependencyDown<CryptoOpsClient>()
            verifyIsDown<StableKeyPairDecryptor>()
        }
    }

    @Test
    fun `When StableKeyPairDecryptor is not UP throws AbstractComponentNotReadyException`() {
        lifecycleTest.run {
            testClass.start()
            bringDependencyUp<CryptoOpsClient>()
            verifyIsUp<StableKeyPairDecryptor>()

            bringDependencyDown<CryptoOpsClient>()
            assertThrows<AbstractComponentNotReadyException>("Component StableKeyPairDecryptor is not ready.") {
                stableKeyPairDecryptor.impl
            }
        }
    }

    // This was behavior with the existing code. Should we change this to ERROR?
    @Test
    fun `On CryptoOpsClient going ERROR, StableKeyPairDecryptor goes DOWN`() {
        lifecycleTest.run {
            testClass.start()
            bringDependencyUp<CryptoOpsClient>()
            verifyIsUp<StableKeyPairDecryptor>()

            setDependencyToError<CryptoOpsClient>()
            verifyIsDown<StableKeyPairDecryptor>()
        }
    }
}
