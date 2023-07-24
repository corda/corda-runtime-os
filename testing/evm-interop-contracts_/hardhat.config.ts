import { HardhatUserConfig } from "hardhat/config";
import "@nomicfoundation/hardhat-toolbox";
import "@nomicfoundation/hardhat-ethers";
const config: HardhatUserConfig = {
  solidity: "0.8.18",
  networks: {
    besu: {
      url: "http://127.0.0.1/rpc",
      accounts: [
        "0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63",
        "0xc87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3",
        "0xae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f",
      ],
      chainId: 1337,
    },
    ganache: {
      url: "http://127.0.0.1:8545",
      accounts: [
        "0x7d671ff20b6770c981c3c3218166d5b53589e15bc278a38ab6ec523b14802deb",
        "0xc053b81dd6eb3484bb33f5264a6ab8b4acdd7727a7ef3f70a6e41dc7712928ed",
        "0x2a9a9def6b690da0f9188179e85f7882d093302045af8dc1083a5c71b041f29f",
      ],
    },
  },
};

export default config;
