pragma solidity ^0.8.0;

import "@openzeppelin/contracts/token/ERC1155/ERC1155.sol";
import "@openzeppelin/contracts/utils/Counters.sol";

contract FractionalOwnershipToken is ERC1155 {
    using Counters for Counters.Counter;
    Counters.Counter private _tokenIds;

    mapping(uint256 => string) public fractionalAssetName;

    constructor() ERC1155("FractionalOwnershipToken") {
        _tokenIds.increment();
    }

    function createToken(address account, uint256 amount, string memory name) external returns (uint256) {
        uint256 newTokenId = _tokenIds.current();
        _mint(account, newTokenId, amount, "");
        fractionalAssetName[newTokenId] = name;
        _tokenIds.increment();
        return newTokenId;
    }
}