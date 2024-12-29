package org.treeWare.metaModel.encoder.openApi

import org.treeWare.metaModel.*
import org.treeWare.metaModel.aux.getResolvedVersionAux
import org.treeWare.metaModel.traversal.Leader1MetaModelVisitor
import org.treeWare.model.core.EntityModel
import org.treeWare.model.core.getMetaModelResolved
import org.treeWare.model.encoder.WireFormatEncoder
import org.treeWare.model.traversal.TraversalAction

class EncodeOpenApiSpecMetaModelVisitor(
    private val encoder: WireFormatEncoder
) : Leader1MetaModelVisitor<TraversalAction> {
    override fun visitMetaModel(leaderMeta1: EntityModel): TraversalAction {
        encoder.encodeObjectStart(null)
        encoder.encodeStringField("openapi", "3.0.1")
        encodeInfo(leaderMeta1)
        encodePaths(leaderMeta1)
        encodeSchemasStart()
        return TraversalAction.CONTINUE
    }

    override fun leaveMetaModel(leaderMeta1: EntityModel) {
        encodeSchemasEnd()
        encoder.encodeObjectEnd()
    }

    override fun visitVersionMeta(leaderVersionMeta1: EntityModel): TraversalAction {
        // The version is encoded earlier (in the `info` section of the OpenAPI spec) in visitMetaModel()
        // using resolved version info.
        return TraversalAction.CONTINUE
    }

    override fun leaveVersionMeta(leaderVersionMeta1: EntityModel) {}

    override fun visitRootMeta(leaderRootMeta1: EntityModel): TraversalAction {
        // The root entity info is encoded earlier (in the `paths` section of the OpenAPI spec) in visitMetaModel()
        // using resolved root info.
        return TraversalAction.CONTINUE
    }

    override fun leaveRootMeta(leaderRootMeta1: EntityModel) {}

    override fun visitPackageMeta(leaderPackageMeta1: EntityModel): TraversalAction {
        // Packages have no explicit presence in an OpenAPI spec. But tree-ware uses it to fully qualify entity names
        // in components/schemas section of the OpenAPI spec.
        return TraversalAction.CONTINUE
    }

    override fun leavePackageMeta(leaderPackageMeta1: EntityModel) {}

    override fun visitEnumerationMeta(leaderEnumerationMeta1: EntityModel): TraversalAction {
        val openApiName = getOpenApiName(leaderEnumerationMeta1)
        encoder.encodeObjectStart(openApiName)
        encoder.encodeStringField("type", "string")
        encoder.encodeListStart("enum")
        return TraversalAction.CONTINUE
    }

    override fun leaveEnumerationMeta(leaderEnumerationMeta1: EntityModel) {
        encoder.encodeListEnd()
        encoder.encodeObjectEnd()
    }

    override fun visitEnumerationValueMeta(leaderEnumerationValueMeta1: EntityModel): TraversalAction {
        val name = getMetaName(leaderEnumerationValueMeta1)
        encoder.encodeStringField("", name)
        return TraversalAction.CONTINUE
    }

    override fun leaveEnumerationValueMeta(leaderEnumerationValueMeta1: EntityModel) {}

    override fun visitEntityMeta(leaderEntityMeta1: EntityModel): TraversalAction {
        val openApiName = getOpenApiName(leaderEntityMeta1)
        encoder.encodeObjectStart(openApiName)
        encoder.encodeStringField("type", "object")
        encoder.encodeObjectStart("properties")
        return TraversalAction.CONTINUE
    }

    override fun leaveEntityMeta(leaderEntityMeta1: EntityModel) {
        encoder.encodeObjectEnd()
        encoder.encodeObjectEnd()
    }

    override fun visitFieldMeta(leaderFieldMeta1: EntityModel): TraversalAction {
        val name = getMetaName(leaderFieldMeta1)
        encoder.encodeObjectStart(name)
        val info = getMetaInfo(leaderFieldMeta1)
        if (info != null) encoder.encodeStringField("description", info)
        when (getFieldTypeMeta(leaderFieldMeta1)) {
            FieldType.BOOLEAN -> encoder.encodeStringField("type", "boolean")
            FieldType.UINT8 -> encoder.encodeStringField("type", "integer")
            FieldType.UINT16 -> encoder.encodeStringField("type", "integer")
            FieldType.UINT32 -> encoder.encodeStringField("type", "integer")
            FieldType.UINT64 -> encoder.encodeStringField("type", "string")
            FieldType.INT8 -> encoder.encodeStringField("type", "integer")
            FieldType.INT16 -> encoder.encodeStringField("type", "integer")
            FieldType.INT32 -> encoder.encodeStringField("type", "integer")
            FieldType.INT64 -> encoder.encodeStringField("type", "string")
            FieldType.FLOAT -> encoder.encodeStringField("type", "number")
            FieldType.DOUBLE -> encoder.encodeStringField("type", "number")
            FieldType.BIG_INTEGER -> encoder.encodeStringField("type", "string")
            FieldType.BIG_DECIMAL -> encoder.encodeStringField("type", "string")
            FieldType.TIMESTAMP -> encoder.encodeStringField("type", "string")
            FieldType.STRING -> encoder.encodeStringField("type", "string")
            FieldType.UUID -> encoder.encodeStringField("type", "string")
            FieldType.BLOB -> encoder.encodeStringField("type", "string")
            FieldType.PASSWORD1WAY -> encoder.encodeStringField("\$ref", "#/components/schemas/password1way")
            FieldType.PASSWORD2WAY -> encoder.encodeStringField("\$ref", "#/components/schemas/password2way")
            FieldType.ALIAS -> throw IllegalStateException("Aliases are not yet supported")
            FieldType.ENUMERATION -> encoder.encodeStringField("type", "string") // TODO
            FieldType.ASSOCIATION -> encoder.encodeStringField("type", "string") // TODO
            FieldType.COMPOSITION -> encoder.encodeStringField("type", "string") // TODO
            null -> throw IllegalStateException("Null field type")
        }
        return TraversalAction.CONTINUE
    }

    override fun leaveFieldMeta(leaderFieldMeta1: EntityModel) {
        encoder.encodeObjectEnd()
    }

    private fun encodeInfo(leaderMeta1: EntityModel) {
        encoder.encodeObjectStart("info")
        val metaModelName = getMetaModelName(leaderMeta1)
        val version = getResolvedVersionAux(leaderMeta1).semantic.toString()
        encoder.encodeStringField("title", metaModelName)
        encoder.encodeStringField("version", version)
        encoder.encodeObjectEnd()
    }

    private fun encodePaths(leaderMeta1: EntityModel) {
        encoder.encodeObjectStart("paths")
        val metaModelName = getMetaModelName(leaderMeta1)
        val rootEntityMeta = getResolvedRootMeta(leaderMeta1)
        val rootEntityOpenApiName = getOpenApiName(rootEntityMeta)
        encodeGetPath(metaModelName, rootEntityOpenApiName)
        encodeSetPath(metaModelName, rootEntityOpenApiName)
        encoder.encodeObjectEnd()
    }

    private fun encodeGetPath(metaModelName: String, rootEntityOpenApiName: String) {
        encoder.encodeObjectStart("/tree-ware/api/get/v1")
        encoder.encodeObjectStart("post")

        encoder.encodeStringField("summary", "Get $metaModelName model")

        encodeRequest(rootEntityOpenApiName)

        encoder.encodeObjectStart("responses")
        encoder.encodeObjectStart("200")
        encoder.encodeStringField("description", "OK")
        encodeRootEntityContent(rootEntityOpenApiName)
        encoder.encodeObjectEnd()
        encoder.encodeObjectEnd()

        encoder.encodeObjectEnd()
        encoder.encodeObjectEnd()
    }

    private fun encodeSetPath(metaModelName: String, rootEntityOpenApiName: String) {
        encoder.encodeObjectStart("/tree-ware/api/set/v1")
        encoder.encodeObjectStart("post")

        encoder.encodeStringField("summary", "Set $metaModelName model")

        encodeRequest(rootEntityOpenApiName)

        encoder.encodeObjectStart("responses")
        encoder.encodeObjectStart("200")
        encoder.encodeStringField("description", "OK")
        encoder.encodeObjectEnd()
        encoder.encodeObjectEnd()

        encoder.encodeObjectEnd()
        encoder.encodeObjectEnd()
    }

    private fun encodeRequest(rootEntityOpenApiName: String) {
        encoder.encodeObjectStart("requestBody")
        encodeRootEntityContent(rootEntityOpenApiName)
        encoder.encodeObjectEnd()
    }

    private fun encodeRootEntityContent(rootEntityOpenApiName: String) {
        encoder.encodeObjectStart("content")
        encoder.encodeObjectStart("application/json")
        encoder.encodeObjectStart("schema")
        encoder.encodeStringField("\$ref", "#/components/schemas/$rootEntityOpenApiName")
        encoder.encodeObjectEnd()
        encoder.encodeObjectEnd()
        encoder.encodeObjectEnd()
    }

    private fun encodeSchemasStart() {
        encoder.encodeObjectStart("components")
        encoder.encodeObjectStart("schemas")
    }

    private fun encodeSchemasEnd() {
        encodePassword1way()
        encodePassword2way()
        encoder.encodeObjectEnd()
        encoder.encodeObjectEnd()
    }

    private fun encodePassword1way() {
        encoder.encodeObjectStart("password1way")
        encodeOneOf("password1way_unhashed", "password1way_hashed")
        encoder.encodeObjectEnd()
        encodePassword1wayUnhashed()
        encodePassword1wayHashed()
    }

    private fun encodePassword1wayUnhashed() {
        encoder.encodeObjectStart("password1way_unhashed")
        encoder.encodeStringField("type", "object")
        encoder.encodeObjectStart("properties")
        encodeProperty("unhashed", "string")
        encoder.encodeObjectEnd()
        encoder.encodeObjectEnd()
    }

    private fun encodePassword1wayHashed() {
        encoder.encodeObjectStart("password1way_hashed")
        encoder.encodeStringField("type", "object")
        encoder.encodeObjectStart("properties")
        encodeProperty("hashed", "string")
        encodeProperty("hash_version", "integer")
        encoder.encodeObjectEnd()
        encoder.encodeObjectEnd()
    }

    private fun encodePassword2way() {
        encoder.encodeObjectStart("password2way")
        encodeOneOf("password2way_unencrypted", "password2way_encrypted")
        encoder.encodeObjectEnd()
        encodePassword2wayUnencrypted()
        encodePassword2wayEncrypted()
    }

    private fun encodePassword2wayUnencrypted() {
        encoder.encodeObjectStart("password2way_unencrypted")
        encoder.encodeStringField("type", "object")
        encoder.encodeObjectStart("properties")
        encodeProperty("unencrypted", "string")
        encoder.encodeObjectEnd()
        encoder.encodeObjectEnd()
    }

    private fun encodePassword2wayEncrypted() {
        encoder.encodeObjectStart("password2way_encrypted")
        encoder.encodeStringField("type", "object")
        encoder.encodeObjectStart("properties")
        encodeProperty("encrypted", "string")
        encodeProperty("cipher_version", "integer")
        encoder.encodeObjectEnd()
        encoder.encodeObjectEnd()
    }

    private fun encodeOneOf(vararg schemaNames: String) {
        encoder.encodeListStart("oneOf")
        schemaNames.forEach { schemaName ->
            encoder.encodeObjectStart("")
            encoder.encodeStringField("\$ref", "#/components/schemas/$schemaName")
            encoder.encodeObjectEnd()
        }
        encoder.encodeListEnd()
    }

    private fun encodeProperty(propertyName: String, propertyType: String) {
        encoder.encodeObjectStart(propertyName)
        encoder.encodeStringField("type", propertyType)
        encoder.encodeObjectEnd()
    }

    private fun getOpenApiName(entityMeta: EntityModel): String {
        val entityFullName = getMetaModelResolved(entityMeta)?.fullName
            ?: throw IllegalStateException("Meta-model not resolved")
        // The above `entityFullName` contains a leading slash and a slash between the package and entity names.
        // Slashes are not permitted in OpenAPI schema names. So drop the leading slash and replace the remaining slash
        // with a dot.
        return entityFullName.drop(1).replace("/", ".")
    }
}