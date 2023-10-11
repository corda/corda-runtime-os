import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.*
import net.corda.libs.configuration.SmartConfig
import net.corda.interop.evm.dispatcher.factory.GenericDispatcherFactory
import net.corda.processor.evm.internal.EVMOpsProcessor
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.CompletableFuture
import org.web3j.EVMTest
import org.web3j.NodeType
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.web3j.protocol.Web3j
import org.web3j.crypto.Credentials
import org.web3j.protocol.http.HttpService
import org.web3j.tx.gas.DefaultGasProvider
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.Order
import org.web3j.utils.Numeric
import java.math.BigInteger


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@EVMTest(type = NodeType.BESU)
class EvmProcessorTest {

    // The address of the ERC20 contract which is being used in these tests
    private lateinit var contractAddress: String

    // The Dispatcher Factory used to handle these requests
    private val dispatcherFactory = GenericDispatcherFactory

    // The Ethereum Operations Processor
    private lateinit var processor: EVMOpsProcessor

    // The main address of the wallet who does all these signing in ets
    private val mainAddress = "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"

    // The other address being used in these tests
    private val otherAddress = "0x627306090abaB3A6e1400e9345bC60c78a8BEf57"

    // The private key of the main address
    private val privateKey = "0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63"

    // The url of the ethereum HTTP JSON RPC
    private val evmRpcUrl = "http://127.0.0.1:8545"

    // The initial expected balance of the owner of the smart contract
    private val ownerInitialBalance = "999999999999999999999990"

    // The allowance amount alloted to the otherAddress from the mainAddress
    private val allowedBalance = "100"

    // The decimals amount set in the smart contract (18)
    private val decimals = "18"

    // The token name "Token"
    private val tokenName =
        "0x00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000005546f6b656e000000000000000000000000000000000000000000000000000000"

    // The token symbol "TKN"
    private val tokenSymbol =
        "0x00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000003544b4e0000000000000000000000000000000000000000000000000000000000"

    // The total supply of tokens in the contract
    private val totalSupply = "1000000000000000000000000"

    private fun waitForTransactionFinality(transactionHash: String): String {
        var hasResult = false
        var receipt = "null"
        while (!hasResult) {

            val transactionHashRequest = EvmRequest(
                mainAddress,
                contractAddress,
                evmRpcUrl,
                "",
                GetTransactionReceipt(transactionHash),
            )
            val transactionHashReceipt = CompletableFuture<EvmResponse>()
            processor.onNext(transactionHashRequest, transactionHashReceipt)
            val transactionReceipt = transactionHashReceipt.get()
            val transactionReceiptResponse = transactionReceipt.payload
            if (transactionReceipt.payload == "null") {
                Thread.sleep(1000)
            } else {
                receipt = transactionReceiptResponse
                hasResult = true
            }
        }
        return receipt
    }

    @BeforeAll
    fun setUp(
    ) {
        val mockedSmartConfig = mock(SmartConfig::class.java)
        whenever(mockedSmartConfig.getInt("maxRetryAttempts")).thenReturn(3)
        whenever(mockedSmartConfig.getLong("maxRetryDelay")).thenReturn(3000.toLong())
        whenever(mockedSmartConfig.getInt("threadPoolSize")).thenReturn(5)
        processor = EVMOpsProcessor(dispatcherFactory, OkHttpClient(), mockedSmartConfig)

        val web3j = Web3j.build(HttpService(evmRpcUrl))
        val credentials = Credentials.create(privateKey)
        val contractGasProvider = DefaultGasProvider()
        val contract = ERC20_sol_ERC20.deploy(web3j, credentials, contractGasProvider).send()
        contractAddress = contract.contractAddress
    }


    @Order(1)
    @Test
    fun transferToContractAddress() {

        val evmRequest = EvmRequest(
            mainAddress,
            contractAddress,
            evmRpcUrl,
            "",
            Transaction(
                "transfer",
                TransactionOptions(
                    "0x47b760",
                    "0",
                    "0x47b760",
                    "0x47b760"
                ),
                listOf(
                    Parameter(
                        "spender",
                        "address",
                        contractAddress
                    ),
                    Parameter(
                        "value",
                        "uint256",
                        "10"
                    ),
                )
            )

        )

        val evmResponse = CompletableFuture<EvmResponse>()
        processor.onNext(evmRequest, evmResponse)
        val transactionReceipt = evmResponse.get().payload
        val receipt = waitForTransactionFinality(transactionReceipt)
        assertNotNull(receipt)
    }


    @Order(2)
    @Test
    fun testBalanceOf() {
        val evmRequest = EvmRequest(
            mainAddress,
            contractAddress,
            evmRpcUrl,
            "uint256",
            Call(
                "balanceOf",
                CallOptions("latest"),
                listOf(
                    Parameter(
                        "account",
                        "address",
                        mainAddress
                    )
                )

            )
        )
        val evmResponse = CompletableFuture<EvmResponse>()
        processor.onNext(evmRequest, evmResponse)
        assertThat(evmResponse.get().payload).isEqualTo(ownerInitialBalance)
    }

    @Order(3)
    @Test
    fun approveFor() {
        val evmRequest = EvmRequest(
            mainAddress,
            contractAddress,
            evmRpcUrl,
            "",
            Transaction(
                "approve",
                TransactionOptions(
                    "0x47b760",
                    "0",
                    "0x47b760",
                    "0x47b760"
                ),
                listOf(
                    Parameter(
                        "spender",
                        "address",
                        otherAddress
                    ),
                    Parameter(
                        "amount",
                        "uint256",
                        "100"
                    ),
                )
            )

        )

        val evmResponse = CompletableFuture<EvmResponse>()
        processor.onNext(evmRequest, evmResponse)

        val transactionReceipt = evmResponse.get().payload
        val receipt = waitForTransactionFinality(transactionReceipt)
        assertNotNull(receipt)
    }


    @Order(4)
    @Test
    fun getApprovedBalance() {
        val evmRequest = EvmRequest(
            mainAddress,
            contractAddress,
            evmRpcUrl,
            "uint256",
            Call(
                "allowance",
                CallOptions("latest"),
                listOf(
                    Parameter(
                        "owner",
                        "address",
                        mainAddress
                    ),
                    Parameter(
                        "spender",
                        "address",
                        otherAddress
                    )
                )

            )
        )
        val evmResponse = CompletableFuture<EvmResponse>()
        processor.onNext(evmRequest, evmResponse)
        assertThat(evmResponse.get().payload).isEqualTo(allowedBalance)
    }


    @Order(5)
    @Test
    fun decimals() {
        val evmRequest = EvmRequest(
            mainAddress,
            contractAddress,
            evmRpcUrl,
            "uint256",
            Call(
                "decimals",
                CallOptions("latest"),
                listOf()

            )
        )
        val evmResponse = CompletableFuture<EvmResponse>()
        processor.onNext(evmRequest, evmResponse)
        assertThat(evmResponse.get().payload).isEqualTo(decimals)
    }

    @Order(6)
    @Test
    fun getName() {
        val evmRequest = EvmRequest(
            mainAddress,
            contractAddress,
            evmRpcUrl,
            "string",
            Call(
                "name",
                CallOptions("latest"),
                listOf()

            )
        )
        val evmResponse = CompletableFuture<EvmResponse>()
        processor.onNext(evmRequest, evmResponse)
        assertThat(evmResponse.get().payload).isEqualTo(tokenName)
    }


    @Order(7)
    @Test
    fun getSymbol() {
        val evmRequest = EvmRequest(
            mainAddress,
            contractAddress,
            evmRpcUrl,
            "string",
            Call(
                "symbol",
                CallOptions("latest"),
                listOf()

            )
        )
        val evmResponse = CompletableFuture<EvmResponse>()
        processor.onNext(evmRequest, evmResponse)
        assertThat(evmResponse.get().payload).isEqualTo(tokenSymbol)
    }


    @Order(8)
    @Test
    fun totalSupply() {
        val evmRequest = EvmRequest(
            mainAddress,
            contractAddress,
            evmRpcUrl,
            "uint256",
            Call(
                "totalSupply",
                CallOptions("latest"),
                listOf()

            )
        )
        val evmResponse = CompletableFuture<EvmResponse>()
        processor.onNext(evmRequest, evmResponse)
        assertThat(evmResponse.get().payload).isEqualTo(totalSupply)
    }


    @Order(9)
    @Test
    fun callWithInvalidParameter() {
        val evmRequest = EvmRequest(
            mainAddress,
            contractAddress,
            evmRpcUrl,
            "uint256",
            Call(
                "balanceOf",
                CallOptions("latest"),
                listOf(
                    Parameter(
                        "account",
                        "uint256",
                        "100"
                    )
                )

            )
        )
        val evmResponse = CompletableFuture<EvmResponse>()

        processor.onNext(evmRequest, evmResponse)
        assertThat(evmResponse.isCompletedExceptionally)
    }


    @Order(10)
    @Test
    fun transferWithInvalidParameters() {
        val evmRequest = EvmRequest(
            mainAddress,
            contractAddress,
            evmRpcUrl,
            "",
            Transaction(
                "transfer",
                TransactionOptions(
                    "0x47b760",
                    "0",
                    "0x47b760",
                    "0x47b760"
                ),
                listOf(
                    Parameter(
                        "spender",
                        "uint256",
                        "10"
                    ),
                    Parameter(
                        "value",
                        "uint256",
                        "10"
                    ),
                )
            )
        )
        val evmResponse = CompletableFuture<EvmResponse>()
        processor.onNext(evmRequest, evmResponse)
        assertThat(evmResponse.isCompletedExceptionally)
    }


    @Order(11)
    @Test
    fun transferWithInsufficientMaxFeePerGas() {
        val evmRequest = EvmRequest(
            mainAddress,
            contractAddress,
            evmRpcUrl,
            "",
            Transaction(
                "transfer",
                TransactionOptions(
                    "0x47b760",
                    "0",
                    "0x47b760",
                    Numeric.toHexStringWithPrefix(BigInteger.valueOf(100))
                ),
                listOf(
                    Parameter(
                        "spender",
                        "uint256",
                        "10"
                    ),
                    Parameter(
                        "value",
                        "uint256",
                        "10"
                    ),
                )
            )
        )
        val evmResponse = CompletableFuture<EvmResponse>()
        processor.onNext(evmRequest, evmResponse)
        assertThat(evmResponse.isCompletedExceptionally)
    }


    @Order(12)
    @Test
    fun transferWithInsufficientGasLimit() {
        val evmRequest = EvmRequest(
            mainAddress,
            contractAddress,
            evmRpcUrl,
            "",
            Transaction(
                "transfer",
                TransactionOptions(
                    Numeric.toHexStringWithPrefix(BigInteger.valueOf(100)),
                    "0x47b760",
                    "0",
                    "0x47b760"
                ),
                listOf(
                    Parameter(
                        "spender",
                        "uint256",
                        "10"
                    ),
                    Parameter(
                        "value",
                        "uint256",
                        "10"
                    ),
                )
            )
        )
        val evmResponse = CompletableFuture<EvmResponse>()
        processor.onNext(evmRequest, evmResponse)
        assertThat(evmResponse.isCompletedExceptionally)
    }


    @Order(13)
    @Test
    fun transferWithExcessiveGasLimit() {
        val evmRequest = EvmRequest(
            mainAddress,
            contractAddress,
            evmRpcUrl,
            "",
            Transaction(
                "transfer",
                TransactionOptions(
                    Numeric.toHexStringWithPrefix(BigInteger.valueOf(100000000000)),
                    "0x47b760",
                    "0",
                    "0x47b760"
                ),
                listOf(
                    Parameter(
                        "spender",
                        "uint256",
                        "10"
                    ),
                    Parameter(
                        "value",
                        "uint256",
                        "10"
                    ),
                )
            )
        )
        val evmResponse = CompletableFuture<EvmResponse>()
        processor.onNext(evmRequest, evmResponse)
        assertThat(evmResponse.isCompletedExceptionally)
    }


}