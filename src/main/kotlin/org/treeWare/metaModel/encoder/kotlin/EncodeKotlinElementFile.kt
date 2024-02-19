package org.treeWare.metaModel.encoder.kotlin

import okio.FileSystem
import okio.Path.Companion.toPath

class EncodeKotlinElementFile(private val elementPackage: EncodeKotlinPackage, private val name: String) {
    private val imports: MutableSet<String> = mutableSetOf()
    private val contents: StringBuilder = StringBuilder()

    var replica: EncodeKotlinElementFile? = null

    fun import(dependency: String): EncodeKotlinElementFile {
        imports.add(dependency)
        replica?.imports?.add(dependency)
        return this
    }

    fun append(string: String): EncodeKotlinElementFile {
        contents.append(string)
        replica?.append(string)
        return this
    }

    fun appendLine(line: String): EncodeKotlinElementFile {
        contents.appendLine(line)
        replica?.appendLine(line)
        return this
    }

    fun write() {
        val file = "${elementPackage.directory}/$name.kt"
        FileSystem.SYSTEM.write(file.toPath()) {
            // Write the package clause.
            if (elementPackage.name.isNotEmpty()) this.writeUtf8("package ").writeUtf8(elementPackage.name)
                .writeUtf8("\n\n")
            // Write the imports in sorted order.
            imports.sorted().forEach { this.writeUtf8("import ").writeUtf8(it).writeUtf8("\n") }
            if (imports.isNotEmpty()) this.writeUtf8("\n")
            // Write the main contents.
            this.writeUtf8(contents.toString())
        }
    }
}