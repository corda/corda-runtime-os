package net.corda.flow.testing.context

import org.osgi.test.common.annotation.InjectService

abstract class FlowServiceTestBase {

    @InjectService(timeout = 4000)
    lateinit var testContext: FlowServiceTestContext


    protected fun given(givenSetup: GivenSetup.() -> Unit){
        testContext.clearTestRuns()
        givenSetup(testContext)
        testContext.execute()
    }

    protected fun `when`(whenSetup: WhenSetup.() -> Unit){
        testContext.clearTestRuns()
        whenSetup(testContext)
        testContext.execute()
    }

    protected fun then(thenSetup: ThenSetup.() -> Unit){
        thenSetup(testContext)
        testContext.assert()
    }
}