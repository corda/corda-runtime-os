package net.corda.web3j.constants

/**
 * This object contains constant values used in the Corda Web3j module for interacting with Ethereum.
 */
/**
 * The JSON-RPC protocol version being used, which is "2.0" for Ethereum.
 */
const val JSON_RPC_VERSION = "2.0"

/**
 * The default RPC ID, typically set to "90.0" for Ethereum.
 */
const val DEFAULT_RPC_ID = "90.0"

/**
 * A temporary private key for testing or internal use. This should not be used in production.
 */
const val TEMPORARY_PRIVATE_KEY = "0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63"

/**
 * JSON-RPC method for retrieving a transaction receipt.
 */
const val GET_TRANSACTION_RECEIPT = "eth_getTransactionReceipt"

/**
 * JSON-RPC method for retrieving the transaction count for an Ethereum address.
 */
const val GET_TRANSACTION_COUNT = "eth_getTransactionCount"

/**
 * JSON-RPC method for retrieving the chain ID.
 */
const val GET_CHAIN_ID = "eth_chainId"

/**
 * JSON-RPC method for sending a raw transaction.
 */
const val SEND_RAW_TRANSACTION = "eth_sendRawTransaction"

/**
 * JSON-RPC method for making a call to a smart contract.
 */
const val CALL = "eth_call"

/**
 * JSON-RPC method for getting the balance of an Ethereum address.
 */
const val ETH_GET_BALANCE = "eth_getBalance"

/**
 * JSON-RPC method for getting the bytecode of a smart contract.
 */
const val ETH_GET_CODE = "eth_getCode"

/**
 * Constant representing the "latest" block parameter when querying blockchain data.
 */
const val LATEST = "latest"
