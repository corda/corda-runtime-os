package net.corda.virtualnode.rest.converters.impl

import net.corda.rest.asynchronous.v1.AsyncOperationState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import net.corda.data.virtualnode.VirtualNodeOperationStatus as AvroVirtualNodeOperationStatus

class MessageConverterTest {

    private val now1 = Instant.ofEpochMilli(1)
    private val now2 = Instant.ofEpochMilli(2)
    private val operationName = "op1"

    @Test
    fun `Convert AvroVirtualNodeOperationStatus with ACCEPTED status`() {
        val avroState = getAvroVirtualNodeOperationStatus("ACCEPTED")

        val result = MessageConverterImpl().convert(avroState, operationName)

        assertThat(result.requestId).isEqualTo("request1")
        assertThat(result.operation).isEqualTo(operationName)
        assertThat(result.status).isEqualTo(AsyncOperationState.ACCEPTED)
        assertThat(result.lastUpdatedTimestamp).isEqualTo(now2)
        assertThat(result.processingStage).isEqualTo(null)
        assertThat(result.errorReason).isEqualTo(null)
        assertThat(result.resourceId).isEqualTo(null)
    }

    @Test
    fun `Convert AvroVirtualNodeOperationStatus with IN_PROGRESS status`() {
        val avroState = getAvroVirtualNodeOperationStatus("IN_PROGRESS")

        val result = MessageConverterImpl().convert(avroState, operationName)

        assertThat(result.requestId).isEqualTo("request1")
        assertThat(result.operation).isEqualTo(operationName)
        assertThat(result.status).isEqualTo(AsyncOperationState.IN_PROGRESS)
        assertThat(result.lastUpdatedTimestamp).isEqualTo(now2)
        assertThat(result.processingStage).isEqualTo(null)
        assertThat(result.errorReason).isEqualTo(null)
        assertThat(result.resourceId).isEqualTo(null)
    }

    @Test
    fun `Convert AvroVirtualNodeOperationStatus with VALIDATION_FAILED status`() {
        val avroState = getAvroVirtualNodeOperationStatus("VALIDATION_FAILED")

        val result = MessageConverterImpl().convert(avroState, operationName)

        assertThat(result.requestId).isEqualTo("request1")
        assertThat(result.operation).isEqualTo(operationName)
        assertThat(result.status).isEqualTo(AsyncOperationState.FAILED)
        assertThat(result.lastUpdatedTimestamp).isEqualTo(now2)
        assertThat(result.processingStage).isEqualTo("Validation")
        assertThat(result.errorReason).isEqualTo("error1")
        assertThat(result.resourceId).isEqualTo(null)
    }

    @Test
    fun `Convert AvroVirtualNodeOperationStatus with LIQUIBASE_DIFF_CHECK_FAILED status`() {
        val avroState = getAvroVirtualNodeOperationStatus("LIQUIBASE_DIFF_CHECK_FAILED")

        val result = MessageConverterImpl().convert(avroState, operationName)

        assertThat(result.requestId).isEqualTo("request1")
        assertThat(result.operation).isEqualTo(operationName)
        assertThat(result.status).isEqualTo(AsyncOperationState.FAILED)
        assertThat(result.lastUpdatedTimestamp).isEqualTo(now2)
        assertThat(result.processingStage).isEqualTo("Liquibase diff check")
        assertThat(result.errorReason).isEqualTo("error1")
        assertThat(result.resourceId).isEqualTo(null)
    }

    @Test
    fun `Convert AvroVirtualNodeOperationStatus with MIGRATIONS_FAILED status`() {
        val avroState = getAvroVirtualNodeOperationStatus("MIGRATIONS_FAILED")

        val result = MessageConverterImpl().convert(avroState, operationName)

        assertThat(result.requestId).isEqualTo("request1")
        assertThat(result.operation).isEqualTo(operationName)
        assertThat(result.status).isEqualTo(AsyncOperationState.FAILED)
        assertThat(result.lastUpdatedTimestamp).isEqualTo(now2)
        assertThat(result.processingStage).isEqualTo("Migration")
        assertThat(result.errorReason).isEqualTo("error1")
        assertThat(result.resourceId).isEqualTo(null)
    }

    @Test
    fun `Convert AvroVirtualNodeOperationStatus with UNEXPECTED_FAILURE status`() {
        val avroState = getAvroVirtualNodeOperationStatus("UNEXPECTED_FAILURE")

        val result = MessageConverterImpl().convert(avroState, operationName)

        assertThat(result.requestId).isEqualTo("request1")
        assertThat(result.operation).isEqualTo(operationName)
        assertThat(result.status).isEqualTo(AsyncOperationState.FAILED)
        assertThat(result.lastUpdatedTimestamp).isEqualTo(now2)
        assertThat(result.processingStage).isEqualTo(null)
        assertThat(result.errorReason).isEqualTo("error1")
        assertThat(result.resourceId).isEqualTo(null)
    }

    @Test
    fun `Convert AvroVirtualNodeOperationStatus with COMPLETED status`() {
        val avroState = getAvroVirtualNodeOperationStatus("COMPLETED")

        val result = MessageConverterImpl().convert(avroState, operationName)

        assertThat(result.requestId).isEqualTo("request1")
        assertThat(result.operation).isEqualTo(operationName)
        assertThat(result.status).isEqualTo(AsyncOperationState.SUCCEEDED)
        assertThat(result.lastUpdatedTimestamp).isEqualTo(now2)
        assertThat(result.processingStage).isEqualTo(null)
        assertThat(result.errorReason).isEqualTo(null)
        assertThat(result.resourceId).isEqualTo(null)
    }

    @Test
    fun `Convert AvroVirtualNodeOperationStatus with ABORTED status`() {
        val avroState = getAvroVirtualNodeOperationStatus("ABORTED")

        val result = MessageConverterImpl().convert(avroState, operationName)

        assertThat(result.requestId).isEqualTo("request1")
        assertThat(result.operation).isEqualTo(operationName)
        assertThat(result.status).isEqualTo(AsyncOperationState.ABORTED)
        assertThat(result.lastUpdatedTimestamp).isEqualTo(now2)
        assertThat(result.processingStage).isEqualTo(null)
        assertThat(result.errorReason).isEqualTo(null)
        assertThat(result.resourceId).isEqualTo(null)
    }

    private fun getAvroVirtualNodeOperationStatus(stateString: String): AvroVirtualNodeOperationStatus {
        return AvroVirtualNodeOperationStatus.newBuilder()
            .setRequestId("request1")
            .setState(stateString)
            .setRequestData("requestData1")
            .setRequestTimestamp(now1)
            .setLatestUpdateTimestamp(now2)
            .setHeartbeatTimestamp(null)
            .setErrors("error1")
            .build()
    }
}