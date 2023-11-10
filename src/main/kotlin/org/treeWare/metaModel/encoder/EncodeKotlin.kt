package org.treeWare.metaModel.encoder

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.Sink
import org.treeWare.model.core.MainModel
import org.treeWare.util.buffered

fun encodeKotlin(mainMeta: MainModel, kotlinDirectoryPath: String) {
    val helloDirectoryPath = "$kotlinDirectoryPath/hello"
    FileSystem.SYSTEM.createDirectories(helloDirectoryPath.toPath())
    val filePath = "$helloDirectoryPath/constants.kt"
    FileSystem.SYSTEM.write(filePath.toPath()) { encodeKotlin(mainMeta, this) }
}

fun encodeKotlin(mainMeta: MainModel, sink: Sink) {
    sink.buffered().use { bufferedSink ->
        bufferedSink.writeUtf8(
            """
            |package hello
            |
            |const val WORLD = "hello world!"
            """.trimMargin()
        )
    }
}
