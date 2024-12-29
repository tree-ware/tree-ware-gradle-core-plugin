package org.treeWare.metaModel.encoder.openApi

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.use
import org.treeWare.metaModel.getMetaModelName
import org.treeWare.metaModel.traversal.metaModelForEach
import org.treeWare.model.core.EntityModel
import org.treeWare.model.encoder.JsonWireFormatEncoder
import org.treeWare.util.buffered

fun encodeOpenApiSpec(mainMeta: EntityModel, directoryPath: String) {
    FileSystem.SYSTEM.createDirectories(directoryPath.toPath())
    val metaName = getMetaModelName(mainMeta)
    val fileName = "$directoryPath/${metaName}_open_api_spec.json"
    FileSystem.SYSTEM.write(fileName.toPath()) {
        this.buffered().use {
            val jsonEncoder = JsonWireFormatEncoder(this, true)
            val encodeVisitor = EncodeOpenApiSpecMetaModelVisitor(jsonEncoder)
            metaModelForEach(mainMeta, encodeVisitor)
        }
    }
}