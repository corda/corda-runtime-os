# Interop Hardhat 

This project is used to compile & test our EVM Smart contracts on either a Hardhat Network or a Hyperledger Besu private blockchain network.
Try running some of the following tasks:

```shell
# Compile the smart contracts
npx hardhat compile

# Test the smart contract on a hardhat test network
npx hardhat test

# Test the smart contract on a private hyperledger besu network (You need to have the network running for this to work)
npx hardhat test --network besu
```
