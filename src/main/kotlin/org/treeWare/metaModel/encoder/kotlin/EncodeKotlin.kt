package org.treeWare.metaModel.encoder.kotlin

import org.treeWare.gradle.MetaModelAuxConfiguration
import org.treeWare.metaModel.traversal.metaModelForEach
import org.treeWare.model.core.EntityModel

fun encodeKotlin(
    metaModelFilePaths: List<String>,
    mainMeta: EntityModel,
    metaModelAuxConfiguration: MetaModelAuxConfiguration,
    kotlinDirectoryPath: String
) {
    val encodeVisitor = EncodeKotlinMetaModelVisitor(metaModelFilePaths, metaModelAuxConfiguration, kotlinDirectoryPath)
    metaModelForEach(mainMeta, encodeVisitor)
}