package org.treeWare.metaModel.encoder.kotlin

import org.treeWare.metaModel.traversal.metaModelForEach
import org.treeWare.model.core.EntityModel

fun encodeKotlin(metaModelFilePaths: List<String>, mainMeta: EntityModel, kotlinDirectoryPath: String) {
    val encodeVisitor = EncodeKotlinMetaModelVisitor(metaModelFilePaths, kotlinDirectoryPath)
    metaModelForEach(mainMeta, encodeVisitor)
}