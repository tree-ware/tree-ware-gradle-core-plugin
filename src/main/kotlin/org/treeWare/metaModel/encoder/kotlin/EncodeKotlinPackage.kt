package org.treeWare.metaModel.encoder.kotlin

import okio.FileSystem
import okio.Path.Companion.toPath
import org.treeWare.metaModel.encoder.util.snakeCaseToLowerCamelCase

class EncodeKotlinPackage(kotlinDirectoryPath: String, treeWarePackageName: String) {
    val name: String = treeWarePackageName.treeWareToKotlinPackageName()
    val directory: String = "$kotlinDirectoryPath/${name.replace(".", "/")}"

    init {
        FileSystem.SYSTEM.createDirectories(directory.toPath())
    }
}

fun String.treeWareToKotlinPackageName(): String =
    this.split(".").joinToString(".") { it.snakeCaseToLowerCamelCase() }