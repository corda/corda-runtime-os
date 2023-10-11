import { ethers } from "hardhat";
import { expect } from "chai";

describe("Deploy & Test Fractional Ownership Contract", function () {
  async function deployERC1155Token() {
    // Contracts are deployed using the first signer/account by default
    const [owner, otherAccount] = await ethers.getSigners();

    // Instantiating the smart contract
    const ERC1155 = await ethers.getContractFactory("FractionalOwnershipToken");
    const erc1155 = await ERC1155.deploy();
    await erc1155.waitForDeployment();

    return { erc1155, owner, otherAccount };
  }

  // Tests to check that minting of a token works as expected
  it("Mint a token", async function () {
    const { erc1155, owner } = await deployERC1155Token();

    await (await erc1155.createToken(owner.address, 1000000,"FractionalAsset")).wait();

    const balance = await erc1155.balanceOf(owner.address, 1);

    const fractionalAssetName = await erc1155.fractionalAssetName(1);

    expect(fractionalAssetName).to.equal("FractionalAsset");
    expect(balance).to.equal(1000000);
  });

  // Test to check the transfer functionality
  it("Transfer token from owner to otherAccount", async function () {
    const { erc1155, owner, otherAccount } = await deployERC1155Token();

    await (await erc1155.createToken(owner.address, 100,"NFT Fractional Asset")).wait();
    await (
      await erc1155.safeTransferFrom(
        owner.address,
        otherAccount.address,
        1,
        50,
        "0x"
      )
    ).wait();

    expect(await erc1155.balanceOf(owner.address, 1)).to.equal(50);
    expect(await erc1155.balanceOf(otherAccount.address, 1)).to.equal(50);
  });


});
