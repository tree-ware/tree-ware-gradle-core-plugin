package org.treeWare.gradle

import okio.FileSystem
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.logging.Logger
import org.treeWare.metaModel.newMetaModelFromJsonFiles
import org.treeWare.model.core.EntityModel

fun getMetaModelFilePaths(resources: SourceDirectorySet): List<String> =
    resources.filter {
        it.parent.endsWith(SOURCE_SET_META_MODEL_DIRECTORY_PATH) && it.extension == SOURCE_SET_META_MODEL_FILE_EXTENSION
    }.files.map { it.path }

fun getMetaModel(metaModelFilePaths: List<String>, logger: Logger): EntityModel? {
    if (metaModelFilePaths.isEmpty()) return null

    val result = newMetaModelFromJsonFiles(
        metaModelFilePaths,
        false,
        null,
        null,
        null,
        emptyList(),
        true,
        FileSystem.SYSTEM
    )
    val metaModel = result.metaModel
    if (metaModel == null) {
        logger.error("Errors while building meta-model:")
        result.errors.forEach { logger.error(it) }
        return null
    }
    return metaModel
}