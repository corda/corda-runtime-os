import { expect } from "chai";
import { ethers } from "hardhat";

describe("ERC20", function () {
  // Deploys the ERC20 Token
  async function deployERC20Token() {
    const [owner, otherAccount] = await ethers.getSigners();

    const ERC20 = await ethers.getContractFactory("Token");
    const erc20 = await ERC20.deploy();
    await erc20.waitForDeployment();

    return { erc20, owner, otherAccount, ethers };
  }

  describe("Deployment", function () {
    it("Should have the correct balance", async function () {
      const { erc20, owner } = await deployERC20Token();
      expect(await erc20.balanceOf(owner.address)).to.equal(
        1000000000000000000000000n
      );
    });


    it("Test transfer to other accounts", async function () {
      const { erc20, otherAccount } = await deployERC20Token();
      await erc20.transfer(otherAccount.address, 1000000000000000000000000n);
    });

    it("Test allowance feature of erc20 token", async function () {
      const { erc20, owner, otherAccount } = await deployERC20Token();
      await (await erc20.approve(otherAccount.address, 1000000000000000000000000n)).wait();

      expect(
        await erc20.allowance(owner.address, otherAccount.address)
      ).to.equal(1000000000000000000000000n);
    });
  });
});
