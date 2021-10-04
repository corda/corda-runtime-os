package net.corda.p2p.gateway.domino

abstract class LeafDominoTile(
    parent: DominoTile?
) :
    DominoTile(parent) {
    override val children: Collection<DominoTile> = emptyList()
}
