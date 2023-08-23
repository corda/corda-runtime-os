import net.corda.data.interop.evm.request.*
import net.corda.libs.configuration.SmartConfig
import net.corda.web3j.dispatcher.factory.GenericDispatcherFactory
import net.corda.processor.evm.internal.EVMOpsProcessor
import okhttp3.OkHttpClient
import org.junit.jupiter.api.TestInstance
import org.web3j.EVMTest
import org.web3j.NodeType
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.web3j.protocol.Web3j
import org.web3j.crypto.Credentials
import org.web3j.protocol.http.HttpService
import org.web3j.tx.gas.DefaultGasProvider


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EVMTest(type = NodeType.BESU)
class StorageStructTest {

    private lateinit var contractAddress: String
    private val dispatcherFactory = GenericDispatcherFactory

    private val privateKey = "0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63"
    private val evmRpcUrl = "http://127.0.0.1:8545"
    private lateinit var processor: EVMOpsProcessor


    @BeforeEach
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
        val contract = Storage_sol_Storage.deploy(web3j, credentials, contractGasProvider).send()
        contractAddress = contract.contractAddress
    }

}