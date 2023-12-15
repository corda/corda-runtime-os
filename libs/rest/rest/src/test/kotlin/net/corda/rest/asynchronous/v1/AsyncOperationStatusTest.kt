package net.corda.rest.asynchronous.v1

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class AsyncOperationStatusTest {

    private val now = Instant.now()

    @Test
    fun `accepted returns populated status`() {
        val target = AsyncOperationStatus.accepted("r1", "op1", now)

        assertThat(target.requestId).isEqualTo("r1")
        assertThat(target.operation).isEqualTo("op1")
        assertThat(target.status).isEqualTo(AsyncOperationState.ACCEPTED)
        assertThat(target.lastUpdatedTimestamp).isEqualTo(now)
        assertThat(target.processingStage).isEqualTo(null)
        assertThat(target.errorReason).isEqualTo(null)
        assertThat(target.resourceId).isEqualTo(null)
    }

    @Test
    fun `inProgress returns populated status`() {
        val target = AsyncOperationStatus.inProgress("r1", "op1", now, "stage1")

        assertThat(target.requestId).isEqualTo("r1")
        assertThat(target.operation).isEqualTo("op1")
        assertThat(target.status).isEqualTo(AsyncOperationState.IN_PROGRESS)
        assertThat(target.lastUpdatedTimestamp).isEqualTo(now)
        assertThat(target.processingStage).isEqualTo("stage1")
        assertThat(target.errorReason).isEqualTo(null)
        assertThat(target.resourceId).isEqualTo(null)
    }

    @Test
    fun `succeeded returns populated status`() {
        val target = AsyncOperationStatus.succeeded("r1", "op1", now, "res1")

        assertThat(target.requestId).isEqualTo("r1")
        assertThat(target.operation).isEqualTo("op1")
        assertThat(target.status).isEqualTo(AsyncOperationState.SUCCEEDED)
        assertThat(target.lastUpdatedTimestamp).isEqualTo(now)
        assertThat(target.processingStage).isEqualTo(null)
        assertThat(target.errorReason).isEqualTo(null)
        assertThat(target.resourceId).isEqualTo("res1")
    }

    @Test
    fun `failed returns populated status`() {
        val target = AsyncOperationStatus.failed("r1", "op1", now, "err1", "stage1")

        assertThat(target.requestId).isEqualTo("r1")
        assertThat(target.operation).isEqualTo("op1")
        assertThat(target.status).isEqualTo(AsyncOperationState.FAILED)
        assertThat(target.lastUpdatedTimestamp).isEqualTo(now)
        assertThat(target.processingStage).isEqualTo("stage1")
        assertThat(target.errorReason).isEqualTo("err1")
        assertThat(target.resourceId).isEqualTo(null)
    }

    @Test
    fun `aborted returns populated status`() {
        val target = AsyncOperationStatus.aborted("r1", "op1", now)

        assertThat(target.requestId).isEqualTo("r1")
        assertThat(target.operation).isEqualTo("op1")
        assertThat(target.status).isEqualTo(AsyncOperationState.ABORTED)
        assertThat(target.lastUpdatedTimestamp).isEqualTo(now)
        assertThat(target.processingStage).isEqualTo(null)
        assertThat(target.errorReason).isEqualTo(null)
        assertThat(target.resourceId).isEqualTo(null)
    }
}
