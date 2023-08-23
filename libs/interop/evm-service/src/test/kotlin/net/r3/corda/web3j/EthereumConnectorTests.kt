package net.r3.corda.web3j

import net.corda.web3j.EthereumConnector
import net.corda.web3j.EvmRPCCall
import net.corda.web3j.GenericResponse
import net.corda.web3j.constants.ETH_GET_BALANCE
import net.corda.web3j.constants.ETH_GET_CODE
import net.corda.web3j.constants.GET_CHAIN_ID
import net.corda.web3j.constants.LATEST
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.junit.jupiter.api.Assertions.assertEquals


class EthereumConnectorTests {

    private lateinit var mockedEVMRpc: EvmRPCCall
    private lateinit var evmConnector: EthereumConnector

    private val rpcUrl = "http://127.0.0.1:8545"
    private val mainAddress = "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"

    @BeforeEach
    fun setUp() {
        mockedEVMRpc = mock(EvmRPCCall::class.java)
        evmConnector = EthereumConnector(mockedEVMRpc)
    }

    @Test
    fun getBalance() {

        val jsonString = "{\"jsonrpc\":\"2.0\",\"id\":\"90.0\",\"result\":\"100000\"}"
        `when`(
            mockedEVMRpc.rpcCall(
                rpcUrl,
                ETH_GET_BALANCE,
                listOf(mainAddress, LATEST)
            )
        ).thenReturn(jsonString)
        val final = evmConnector.send<GenericResponse>(
            rpcUrl,
            ETH_GET_BALANCE,
            listOf(mainAddress, LATEST)
        )
        assertEquals("100000", final.result)
    }


    @Test
    fun getCode() {
        val mockedEVMRpc = mock(EvmRPCCall::class.java)
        val evmConnector = EthereumConnector(mockedEVMRpc)
        val jsonString = "{\"jsonrpc\":\"2.0\",\"id\":\"90.0\",\"result\":\"0xfd2ds\"}"
        `when`(
            mockedEVMRpc.rpcCall(
                rpcUrl,
                ETH_GET_CODE,
                listOf(mainAddress, "0x1")
            )
        ).thenReturn(jsonString)
        val final = evmConnector.send<GenericResponse>(
            rpcUrl,
            ETH_GET_CODE,
            listOf(mainAddress, "0x1")
        )
        assertEquals("0xfd2ds", final.result)
    }


    @Test
    fun getChainId() {
        val mockedEVMRpc = mock(EvmRPCCall::class.java)
        val evmConnector = EthereumConnector(mockedEVMRpc)
        val jsonString = "{\"jsonrpc\":\"2.0\",\"id\":\"90.0\",\"result\":\"1337\"}"
        `when`(
            mockedEVMRpc.rpcCall(
                rpcUrl,
                GET_CHAIN_ID,
                emptyList<String>()
            )
        ).thenReturn(jsonString)
        val final = evmConnector.send<GenericResponse>(rpcUrl, GET_CHAIN_ID, emptyList<String>())
        assertEquals("1337", final.result)
    }


}