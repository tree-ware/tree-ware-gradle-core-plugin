package org.treeWare.metaModel.encoder.kotlin

import org.treeWare.metaModel.traversal.metaModelForEach
import org.treeWare.model.core.MainModel

fun encodeKotlin(mainMeta: MainModel, kotlinDirectoryPath: String) {
    val encodeVisitor = EncodeKotlinMetaModelVisitor(kotlinDirectoryPath)
    metaModelForEach(mainMeta, encodeVisitor)
}