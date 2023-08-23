import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Int256;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

/**
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the
 * <a href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 1.4.2.
 */
@SuppressWarnings("rawtypes")
public class Storage_sol_Storage extends Contract {
    public static final String BINARY = "608060405234801561000f575f80fd5b506103258061001d5f395ff3fe608060405234801561000f575f80fd5b506004361061003f575f3560e01c80630e1819a9146100435780632e64cec11461008e5780636e7abac4146100a3575b5f80fd5b61008c61005136600461020e565b5f9182553382526001602081815260409093208251815591830151805191830191909155820151805160028301559091015151600390910155565b005b5f546040519081526020015b60405180910390f35b6100b66100b13660046102c2565b6100e8565b60405161009a919081518152602091820151805183830152820151805160408301529091015151606082015260800190565b6100f0610152565b506001600160a01b03165f90815260016020818152604092839020835180850185528154815284518086018652938201548452845180860186526002830154815285518085019096526003909201548552818301949094528282015282015290565b60405180604001604052805f815260200161016b610170565b905290565b60405180604001604052805f815260200161016b60405180604001604052805f815260200161016b60405180602001604052805f81525090565b6040805190810167ffffffffffffffff811182821017156101d957634e487b7160e01b5f52604160045260245ffd5b60405290565b6040516020810167ffffffffffffffff811182821017156101d957634e487b7160e01b5f52604160045260245ffd5b5f8082840360a0811215610220575f80fd5b833592506080601f1982011215610235575f80fd5b61023d6101aa565b602085013581526060603f1983011215610255575f80fd5b61025d6101aa565b6040868101358252605f1984011215610274575f80fd5b61027c6101aa565b606087013581526020607f1985011215610294575f80fd5b61029c6101df565b608097909701358752602081810197909752818701529481019490945250909391925050565b5f602082840312156102d2575f80fd5b81356001600160a01b03811681146102e8575f80fd5b939250505056fea2646970667358221220bcc13033c8000ebdbe7ae47753236bc2d3020fcdf9b9217e7b0c260ffdd0f62b64736f6c63430008140033";

    public static final String FUNC_RETRIEVE = "retrieve";

    public static final String FUNC_RETRIEVESTRUCT = "retrieveStruct";

    public static final String FUNC_STORE = "store";

    @Deprecated
    protected Storage_sol_Storage(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected Storage_sol_Storage(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected Storage_sol_Storage(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected Storage_sol_Storage(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public RemoteFunctionCall<BigInteger> retrieve() {
        final Function function = new Function(FUNC_RETRIEVE,
                Arrays.<Type>asList(),
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<struct1> retrieveStruct(String name) {
        final Function function = new Function(FUNC_RETRIEVESTRUCT,
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, name)),
                Arrays.<TypeReference<?>>asList(new TypeReference<struct1>() {}));
        return executeRemoteCallSingleValueReturn(function, struct1.class);
    }

    public RemoteFunctionCall<TransactionReceipt> store(BigInteger num, struct1 name) {
        final Function function = new Function(
                FUNC_STORE,
                Arrays.<Type>asList(new Uint256(num),
                        name),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    @Deprecated
    public static Storage_sol_Storage load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new Storage_sol_Storage(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static Storage_sol_Storage load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new Storage_sol_Storage(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static Storage_sol_Storage load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new Storage_sol_Storage(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static Storage_sol_Storage load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new Storage_sol_Storage(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<Storage_sol_Storage> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(Storage_sol_Storage.class, web3j, credentials, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<Storage_sol_Storage> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(Storage_sol_Storage.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
    }

    public static RemoteCall<Storage_sol_Storage> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(Storage_sol_Storage.class, web3j, transactionManager, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<Storage_sol_Storage> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(Storage_sol_Storage.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
    }

    public static class struct4 extends StaticStruct {
        public BigInteger x4;

        public struct4(BigInteger x4) {
            super(new Int256(x4));
            this.x4 = x4;
        }

        public struct4(Int256 x4) {
            super(x4);
            this.x4 = x4.getValue();
        }
    }

    public static class struct3 extends StaticStruct {
        public BigInteger x3;

        public struct4 struct4;

        public struct3(BigInteger x3, struct4 struct4) {
            super(new Int256(x3),
                    struct4);
            this.x3 = x3;
            this.struct4 = struct4;
        }

        public struct3(Int256 x3, struct4 struct4) {
            super(x3, struct4);
            this.x3 = x3.getValue();
            this.struct4 = struct4;
        }
    }

    public static class struct2 extends StaticStruct {
        public BigInteger x2;

        public struct3 struct3;

        public struct2(BigInteger x2, struct3 struct3) {
            super(new Int256(x2),
                    struct3);
            this.x2 = x2;
            this.struct3 = struct3;
        }

        public struct2(Int256 x2, struct3 struct3) {
            super(x2, struct3);
            this.x2 = x2.getValue();
            this.struct3 = struct3;
        }
    }

    public static class struct1 extends StaticStruct {
        public BigInteger x1;

        public struct2 struct2;

        public struct1(BigInteger x1, struct2 struct2) {
            super(new Int256(x1),
                    struct2);
            this.x1 = x1;
            this.struct2 = struct2;
        }

        public struct1(Int256 x1, struct2 struct2) {
            super(x1, struct2);
            this.x1 = x1.getValue();
            this.struct2 = struct2;
        }
    }
}
