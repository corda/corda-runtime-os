// SPDX-License-Identifier: GPL-3.0

pragma solidity >=0.8.2 <0.9.0;

/**
 * @title Storage
 * @dev Store & retrieve value in a variable
 * @custom:dev-run-script ./scripts/deploy_with_ethers.ts
 */
contract Storage {

    uint256 number;

    struct struct4 {
        int x4;
    }

    struct struct3 {
        int x3;
        struct4 struct4;
    }

    struct struct2 {
        int x2;
        struct3 struct3;
    }

    struct struct1 {
        int x1;
        struct2 struct2;
    }

    mapping (address => struct1) structMapper;
    /**
     * @dev Store value in variable
     * @param num value to store
     */
    function store(uint256 num, struct1 memory name) public {
        number = num;
        structMapper[msg.sender] = name;
    }


    function retrieveStruct(address name) public view returns(struct1 memory){
        return structMapper[name];
    }
    /**
     * @dev Return value 
     * @return value of 'number'
     */
    function retrieve() public view returns (uint256){
        return number;
    }
}