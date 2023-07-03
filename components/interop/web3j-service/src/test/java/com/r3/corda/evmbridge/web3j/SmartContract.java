package com.r3.corda.evmbridge.web3j;

import java.util.Map;

public interface SmartContract {
    void execute(String functionName);
    void execute(String functionName, Map<String, Object> parameters);
}
