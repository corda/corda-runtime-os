import { ethers } from "hardhat";
import { expect } from "chai";

describe("Deploy & Test ERC1155", function () {
  async function deployERC1155Token() {
    // Contracts are deployed using the first signer/account by default
    const [owner, otherAccount] = await ethers.getSigners();

    // Instantiating the smart contract
    const ERC1155 = await ethers.getContractFactory("MyERC1155Token");
    const erc1155 = await ERC1155.deploy();
    await erc1155.waitForDeployment();

    return { erc1155, owner, otherAccount };
  }

  // Tests to check that minting of a token works as expected
  it("Mint a token", async function () {
    const { erc1155, owner } = await deployERC1155Token();

    await (await erc1155.createToken(owner.address, 100)).wait();

    const balance = await erc1155.balanceOf(owner.address, 1);

    expect(balance).to.equal(100);
  });

  // Test to check the transfer functionality
  it("Transfer token from owner to otherAccount", async function () {
    const { erc1155, owner, otherAccount } = await deployERC1155Token();

    await (await erc1155.createToken(owner.address, 100)).wait();
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

  // approve transfer to transact tokens
  it("Approve transfer to transact tokens", async function () {
    const { erc1155, owner, otherAccount } = await deployERC1155Token();

    await (await erc1155.createToken(owner.address, 100)).wait();
    await (await erc1155.setApprovalForAll(otherAccount.address, true)).wait();

    expect(
      await erc1155.isApprovedForAll(owner.address, otherAccount.address)
    ).to.equal(true);
    await (
      await erc1155.safeTransferFrom(
        owner.address,
        otherAccount.address,
        1,
        50,
        "0x"
      )
    ).wait();
    // Checkng the successfull transfer of the approved token
    expect(await erc1155.balanceOf(owner.address, 1)).to.equal(50);
    expect(await erc1155.balanceOf(otherAccount.address, 1)).to.equal(50);
  });

  // Testing Batch Transfers with Approval Mechanism 
  it("Test batch transfers", async function () {
    const { erc1155, owner, otherAccount } = await deployERC1155Token();
    
    await (await erc1155.createToken(owner.address, 100)).wait();
    await (await erc1155.setApprovalForAll(otherAccount.address, true)).wait();

    expect(
      await erc1155.isApprovedForAll(owner.address, otherAccount.address)
    ).to.equal(true);

    // Testing the transfer of the token
    await (
      await erc1155.safeBatchTransferFrom(
        owner.address,
        otherAccount.address,
        [1],
        [50],
        "0x"
      )
    ).wait();
    
    expect(await erc1155.balanceOf(owner.address, 1)).to.equal(50);
    expect(await erc1155.balanceOf(otherAccount.address, 1)).to.equal(50);
  });
});
