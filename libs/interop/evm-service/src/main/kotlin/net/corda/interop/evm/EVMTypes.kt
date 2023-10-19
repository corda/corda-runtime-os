package net.corda.interop.evm

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import net.corda.v5.base.exceptions.CordaRuntimeException

class EVMErrorException(val errorResponse: JsonRpcError) : CordaRuntimeException(errorResponse.error.toString())

data class JsonRpcError @JsonCreator constructor(
    @JsonProperty("jsonrpc") val jsonrpc: String,
    @JsonProperty("id") val id: String,
    @JsonProperty("error") val error: Error,
) : EvmResponse

data class Error @JsonCreator constructor(
    @JsonProperty("code") val code: Int,
    @JsonProperty("message") val message: String,
    @JsonProperty("data") val data: String?,
)

data class RpcRequest @JsonCreator constructor(
    @JsonProperty("jsonrpc") val jsonrpc: String,
    @JsonProperty("id") val id: String,
    @JsonProperty("method") val method: String,
    @JsonProperty("params") val params: List<*>,
)


interface EvmResponse

data class GenericResponse @JsonCreator constructor(
    @JsonProperty("id") val id: String,
    @JsonProperty("jsonrpc") val jsonrpc: String,
    @JsonProperty("result") val result: String?,
)

data class Response @JsonCreator constructor(
    @JsonProperty("id") val id: String,
    @JsonProperty("jsonrpc") val jsonrpc: String,
    @JsonProperty("result") val result: TransactionData,
)

data class TransactionData @JsonCreator constructor(
    @JsonProperty("blockHash") val blockHash: String,
    @JsonProperty("blockNumber") val blockNumber: String,
    @JsonProperty("contractAddress") val contractAddress: String?,
    @JsonProperty("cumulativeGasUsed") val cumulativeGasUsed: String,
    @JsonProperty("from") val from: String,
    @JsonProperty("gasUsed") val gasUsed: String,
    @JsonProperty("effectiveGasPrice") val effectiveGasPrice: String,
    @JsonProperty("logs") val logs: List<TransactionLog>,
    @JsonProperty("logsBloom") val logsBloom: String,
    @JsonProperty("status") val status: String,
    @JsonProperty("to") val to: String?,
    @JsonProperty("transactionHash") val transactionHash: String,
    @JsonProperty("transactionIndex") val transactionIndex: String,
    @JsonProperty("type") val type: String,
    @JsonProperty("extDataGasUsed") val extDataGasUsed: String?,
) : EvmResponse

data class TransactionLog @JsonCreator constructor(
    @JsonProperty("address") val address: String,
    @JsonProperty("topics") val topics: List<String>,
    @JsonProperty("data") val data: String,
    @JsonProperty("blockNumber") val blockNumber: String,
    @JsonProperty("transactionHash") val transactionHash: String,
    @JsonProperty("transactionIndex") val transactionIndex: String,
    @JsonProperty("blockHash") val blockHash: String,
    @JsonProperty("logIndex") val logIndex: String,
    @JsonProperty("removed") val removed: Boolean,
    @JsonProperty("extDataGasUsed") val extDataGasUsed: String?,
)


