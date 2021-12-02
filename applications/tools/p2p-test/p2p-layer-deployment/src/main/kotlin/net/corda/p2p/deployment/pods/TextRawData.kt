package net.corda.p2p.deployment.pods
data class TextFile(val fileName: String, val content: String) : RawFile

class TextRawData(
    name: String,
    dirName: String,
    content: Collection<TextFile>
) : RawData<TextFile>(
    name, dirName, content
) {
    override val data = mapOf(
        "data" to content.associate {
            it.fileName to it.content
        }
    )
}
