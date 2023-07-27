pragma solidity ^0.8.0;

import "@openzeppelin/contracts/token/ERC1155/ERC1155.sol";
import "@openzeppelin/contracts/utils/Counters.sol";

contract MyERC1155Token is ERC1155 {
    using Counters for Counters.Counter;
    Counters.Counter private _tokenIds;

    constructor() ERC1155("") {
        _tokenIds.increment();
    }

    function createToken(address account, uint256 amount) external returns (uint256) {
        uint256 newTokenId = _tokenIds.current();
        _mint(account, newTokenId, amount, "");

        _tokenIds.increment();
        return newTokenId;
    }
}