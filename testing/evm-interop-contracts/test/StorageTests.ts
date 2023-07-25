import { expect } from "chai";
import { ethers } from "hardhat";

describe("Storage", function () {

  // Deploys the Storage Contract
  async function deployStorage() {
    const [owner, otherAccount] = await ethers.getSigners();
    const Storage = await ethers.getContractFactory("Storage");
    const storage = await Storage.deploy();
    await storage.waitForDeployment();
    return { storage, owner, otherAccount };
  }

  it("Test setting storage of nested struct", async function () {
    const { storage } = await deployStorage();
    const num: number = 42;
    const struct1 = {
      x1: 10,
      struct2: {
        x2: 20,
        struct3: {
          x3: 30,
          struct4: {
            x4: 40,
          },
        },
      },
    };
    await (await storage.store(num, struct1)).wait();

    const storedNumber = await storage.retrieve();

    expect(storedNumber).to.equal(num);
  });
});

