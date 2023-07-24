pragma solidity ^0.8.2;
import "@openzeppelin/contracts/token/ERC20/ERC20.sol";




contract Token is ERC20 {

    address owner;
    constructor () ERC20("Token", "TKN") {
        _mint(msg.sender, 1000000 * (10 ** uint256(decimals())));
        owner = msg.sender;
    }


    function getEverything() public view returns (address,  uint256, string memory) {
        return (owner, 100, "hello world");
    }
}
