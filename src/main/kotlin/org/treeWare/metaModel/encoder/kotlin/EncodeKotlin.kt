package org.treeWare.metaModel.encoder.kotlin

import org.treeWare.metaModel.traversal.metaModelForEach
import org.treeWare.model.core.MainModel

fun encodeKotlin(metaModelFilePaths: List<String>, mainMeta: MainModel, kotlinDirectoryPath: String) {
    val encodeVisitor = EncodeKotlinMetaModelVisitor(metaModelFilePaths, kotlinDirectoryPath)
    metaModelForEach(mainMeta, encodeVisitor)
}