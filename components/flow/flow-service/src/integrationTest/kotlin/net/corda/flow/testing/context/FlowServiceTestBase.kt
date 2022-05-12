package net.corda.flow.testing.context

import org.junit.jupiter.api.BeforeEach
import org.osgi.test.common.annotation.InjectService

abstract class FlowServiceTestBase {

    @InjectService(timeout = 4000)
    lateinit var testContext: FlowServiceTestContext

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        testContext.resetTestContext()
        testContext.start()
    }

    protected fun given(stepSetup: StepSetup.() -> Unit){
        testContext.clearTestRuns()
        stepSetup(testContext)
        testContext.execute()
    }

    protected fun `when`(stepSetup: StepSetup.() -> Unit){
        testContext.clearTestRuns()
        stepSetup(testContext)
        testContext.execute()
    }

    protected fun then(thenSetup: ThenSetup.() -> Unit){
        testContext.clearAssertions()
        thenSetup(testContext)
        testContext.assert()
    }
}