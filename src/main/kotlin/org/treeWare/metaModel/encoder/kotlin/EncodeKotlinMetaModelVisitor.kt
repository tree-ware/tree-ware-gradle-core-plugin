package org.treeWare.metaModel.encoder.kotlin

import org.treeWare.metaModel.*
import org.treeWare.metaModel.encoder.util.snakeCaseToLowerCamelCase
import org.treeWare.metaModel.encoder.util.snakeCaseToUpperCamelCase
import org.treeWare.metaModel.traversal.Leader1MetaModelVisitor
import org.treeWare.model.core.EntityModel
import org.treeWare.model.core.getMetaModelResolved
import org.treeWare.model.core.getSingleString
import org.treeWare.model.traversal.TraversalAction

class EncodeKotlinMetaModelVisitor(
    private val metaModelFilePaths: List<String>,
    private val kotlinDirectoryPath: String
) : Leader1MetaModelVisitor<TraversalAction> {

    private lateinit var rootKotlinType: KotlinType

    // region Leader1MetaModelVisitor methods

    override fun visitMetaModel(leaderMeta1: EntityModel): TraversalAction {
        val treeWarePackageName = "" // TODO add support for package_name for main-model
        mainModelPackage = EncodeKotlinPackage(kotlinDirectoryPath, treeWarePackageName)
        rootKotlinType = getEntityInfoKotlinType(getEntityInfoMeta(leaderMeta1, "root"))
        val kotlinMetaModelConstant = writeKotlinMetaModelFile(leaderMeta1)
        dslMarkerName = getDslMarkerName(leaderMeta1)
        writeKotlinDslMarkerFile()
        writeKotlinDslModelFile(leaderMeta1, kotlinMetaModelConstant)
        return TraversalAction.CONTINUE
    }

    override fun leaveMetaModel(leaderMeta1: EntityModel) {}

    override fun visitVersionMeta(leaderVersionMeta1: EntityModel): TraversalAction = TraversalAction.CONTINUE

    override fun leaveVersionMeta(leaderVersionMeta1: EntityModel) {}

    override fun visitRootMeta(leaderRootMeta1: EntityModel): TraversalAction = TraversalAction.CONTINUE

    override fun leaveRootMeta(leaderRootMeta1: EntityModel) {}

    override fun visitPackageMeta(leaderPackageMeta1: EntityModel): TraversalAction {
        val treeWarePackageName = getMetaName(leaderPackageMeta1)
        elementPackage = EncodeKotlinPackage(kotlinDirectoryPath, treeWarePackageName)
        return TraversalAction.CONTINUE
    }

    override fun leavePackageMeta(leaderPackageMeta1: EntityModel) {
        // Nothing to do.
    }

    override fun visitEnumerationMeta(leaderEnumerationMeta1: EntityModel): TraversalAction {
        val enumerationName = getMetaName(leaderEnumerationMeta1).snakeCaseToUpperCamelCase()
        enumerationFile = EncodeKotlinElementFile(elementPackage, enumerationName)
        enumerationFile.appendLine("enum class $enumerationName {")
        return TraversalAction.CONTINUE
    }

    override fun leaveEnumerationMeta(leaderEnumerationMeta1: EntityModel) {
        enumerationFile.append("}")
        enumerationFile.write()
    }

    override fun visitEnumerationValueMeta(leaderEnumerationValueMeta1: EntityModel): TraversalAction {
        val name = getMetaName(leaderEnumerationValueMeta1).uppercase()
        enumerationFile.appendLine("    $name,")
        return TraversalAction.CONTINUE
    }

    override fun leaveEnumerationValueMeta(leaderEnumerationValueMeta1: EntityModel) {
        // Nothing to do.
    }

    override fun visitEntityMeta(leaderEntityMeta1: EntityModel): TraversalAction {
        val entityInterfaceName = getMetaName(leaderEntityMeta1).snakeCaseToUpperCamelCase()
        val isRootEntity = rootKotlinType.interfaceType == "${elementPackage.name}.$entityInterfaceName"

        entityInterfaceFile = EncodeKotlinElementFile(elementPackage, entityInterfaceName)
        entityInterfaceFile.import("org.treeWare.model.core.*")
        entityInterfaceFile.appendLine("interface $entityInterfaceName : EntityModel {")

        val entityMutableClassName = "Mutable$entityInterfaceName"
        entityMutableClassFile = EncodeKotlinElementFile(elementPackage, entityMutableClassName)
        entityMutableClassFile.import("org.treeWare.model.core.*")
        entityMutableClassFile.import(dslMarkerName)
        entityMutableClassFile.appendLine(
            """
            |@$dslMarkerName
            |class $entityMutableClassName(
            |    meta: EntityModel,
            |    parent: MutableFieldModel${if (isRootEntity) "?" else ""}
            |) : $entityInterfaceName, MutableEntityModel(meta, parent) {
            """.trimMargin()
        )
        entityGetCompositionFactoryMethod = StringBuilder()
        entityGetCompositionFactoryMethod.appendLine(
            """
            |    override fun getCompositionFactory(fieldName: String, fieldMeta: EntityModel): FieldValueFactory =
            |        when (fieldName) {
            """.trimMargin()
        )
        return TraversalAction.CONTINUE
    }

    override fun leaveEntityMeta(leaderEntityMeta1: EntityModel) {
        val metaName = getMetaName(leaderEntityMeta1)
        val entityInterfaceName = metaName.snakeCaseToUpperCamelCase()
        val entityMutableClassName = "Mutable$entityInterfaceName"
        val functionName = metaName.snakeCaseToLowerCamelCase()

        entityInterfaceFile.append("}")
        entityInterfaceFile.write()

        entityGetCompositionFactoryMethod.append(
            """
            |            // New fields might have been added to the meta-model after this code was generated.
            |            // Handle those new fields by calling super.
            |            else -> super.getCompositionFactory(fieldName, fieldMeta)
            |        }
            """.trimMargin()
        )

        entityMutableClassFile.appendLine("").appendLine("    // region Framework helpers")
        entityMutableClassFile.appendLine("").appendLine(entityGetCompositionFactoryMethod.toString())
        entityMutableClassFile.appendLine("").appendLine(
            """
            |    companion object {
            |        val fieldValueFactory: FieldValueFactory =
            |            { fieldMeta, parent ->
            |                val entityMeta = getMetaModelResolved(fieldMeta)?.compositionMeta
            |                    ?: throw IllegalStateException("Field composition is not resolved")
            |                $entityMutableClassName(entityMeta, parent)
            |            }
            |    }
            """.trimMargin()
        )
        entityMutableClassFile.appendLine("").appendLine(
            """
            |    class SetBuilder(val setField: MutableSetFieldModel) {
            |        fun $functionName(configure: $entityMutableClassName.() -> Unit) {
            |            val entity = setField.getOrNewValue() as $entityMutableClassName
            |            entity.configure()
            |            setField.addValue(entity)
            |        }
            |    }
            """.trimMargin()
        )
        entityMutableClassFile.appendLine("").appendLine("    // endregion")
        entityMutableClassFile.append("}")
        entityMutableClassFile.write()
    }

    override fun visitFieldMeta(leaderFieldMeta1: EntityModel): TraversalAction {
        val fieldNameTreeWare = getMetaName(leaderFieldMeta1)
        val fieldNameKotlin = fieldNameTreeWare.snakeCaseToLowerCamelCase()
        val info = getMetaInfo(leaderFieldMeta1)?.trim() ?: ""
        val fieldType = getFieldTypeMeta(leaderFieldMeta1) ?: throw IllegalStateException("Field type not defined")
        val valueKotlinType = getFieldKotlinType(leaderFieldMeta1)
        val multiplicity = getMultiplicityMeta(leaderFieldMeta1)
        val fieldKotlinType = getMultiplicityKotlinType(multiplicity, valueKotlinType)
        entityInterfaceFile.appendLine("")
        entityMutableClassFile.appendLine("")
        if (info != "") {
            entityInterfaceFile.appendLine("    /** $info */")
            entityMutableClassFile.appendLine("    /** $info */")
        }
        entityInterfaceFile.appendLine("    val $fieldNameKotlin: ${fieldKotlinType.interfaceType}?")
        encodeField(
            leaderFieldMeta1,
            info,
            fieldNameTreeWare,
            fieldNameKotlin,
            fieldType,
            fieldKotlinType,
            multiplicity,
            valueKotlinType
        )
        if (fieldType == FieldType.COMPOSITION) entityGetCompositionFactoryMethod.appendLine(
            """            "$fieldNameTreeWare" -> ${valueKotlinType.mutableClassType}.fieldValueFactory"""
        )
        return TraversalAction.CONTINUE
    }

    override fun leaveFieldMeta(leaderFieldMeta1: EntityModel) {
        // Nothing to do.
    }

    // endregion

    // region Helper methods

    private fun getDslMarkerName(meta: EntityModel): String {
        val metaModelName = getMetaModelName(meta)
        return metaModelName.snakeCaseToUpperCamelCase() + "DslMarker"
    }

    private fun writeKotlinMetaModelFile(meta: EntityModel): String {
        val metaModelName = getMetaModelName(meta)
        val fileName = metaModelName.snakeCaseToUpperCamelCase() + "MetaModel"
        val file = EncodeKotlinElementFile(mainModelPackage, fileName)
        file.import("org.treeWare.metaModel.newMetaModelFromJsonFiles")
        file.import("org.treeWare.model.core.*")

        val metaModelFilesConstant = metaModelName.uppercase() + "_META_MODEL_FILES"
        file.append("val ").append(metaModelFilesConstant).appendLine(" = listOf(")
        this.metaModelFilePaths.sorted().forEach { absolutePath ->
            val splits = absolutePath.split("resources/")
            val relativePath = if (splits.size > 1) splits[1] else splits[0]
            file.append("    \"").append(relativePath).appendLine("\",")
        }
        file.appendLine(")")
        file.appendLine("")

        val kotlinMetaModelConstant = metaModelName.snakeCaseToLowerCamelCase() + "MetaModel"
        file.append("val ").append(kotlinMetaModelConstant).appendLine(" = newMetaModelFromJsonFiles(")
        file.append("    ").append(metaModelFilesConstant)
        file.appendLine(", false, null, null, ::rootEntityFactory, emptyList(), true")
        file.appendLine(").metaModel ?: throw IllegalStateException(\"Meta-model has validation errors\")")

        // rootEntityFactory() function.
        file.appendLine(
            """
            |
            |fun rootEntityFactory(rootMeta: EntityModel, parent: MutableFieldModel) =
            |    ${rootKotlinType.mutableClassType}(rootMeta, parent)
            """.trimMargin()
        )

        file.write()
        return kotlinMetaModelConstant
    }

    private fun writeKotlinDslMarkerFile() {
        val file = EncodeKotlinElementFile(mainModelPackage, dslMarkerName)
        file.appendLine("@DslMarker")
        file.appendLine("annotation class $dslMarkerName")
        file.write()
    }

    private fun writeKotlinDslModelFile(meta: EntityModel, kotlinMetaModelConstant: String) {
        val metaModelName = getMetaModelName(meta)
        val fileName = metaModelName.snakeCaseToUpperCamelCase()
        val file = EncodeKotlinElementFile(mainModelPackage, fileName)
        file.import("org.treeWare.metaModel.getResolvedRootMeta")
        file.import(dslMarkerName)
        file.appendLine(
            """
            |@$dslMarkerName
            |fun ${metaModelName.snakeCaseToLowerCamelCase()}(configure: ${rootKotlinType.mutableClassType}.() -> Unit): ${rootKotlinType.mutableClassType} {
            |    val rootMeta = getResolvedRootMeta($kotlinMetaModelConstant)
            |    val root = ${rootKotlinType.mutableClassType}(rootMeta, null)
            |    root.configure()
            |    return root
            |}
            """.trimMargin()
        )
        file.write()
    }

    private fun getFieldKotlinType(fieldMeta: EntityModel): KotlinType {
        return when (getFieldTypeMeta(fieldMeta)) {
            FieldType.BOOLEAN -> KotlinType("Boolean", "Boolean")
            FieldType.UINT8 -> KotlinType("UByte", "UByte")
            FieldType.UINT16 -> KotlinType("UShort", "UShort")
            FieldType.UINT32 -> KotlinType("UInt", "UInt")
            FieldType.UINT64 -> KotlinType("ULong", "ULong")
            FieldType.INT8 -> KotlinType("Byte", "Byte")
            FieldType.INT16 -> KotlinType("Short", "Short")
            FieldType.INT32 -> KotlinType("Int", "Int")
            FieldType.INT64 -> KotlinType("Long", "Long")
            FieldType.FLOAT -> KotlinType("Float", "Float")
            FieldType.DOUBLE -> KotlinType("Double", "Double")
            FieldType.BIG_INTEGER -> KotlinType("java.math.BigInteger", "java.math.BigInteger")
            FieldType.BIG_DECIMAL -> KotlinType("java.math.BigDecimal", "java.math.BigDecimal")
            FieldType.TIMESTAMP -> KotlinType("ULong", "ULong")
            FieldType.STRING -> KotlinType("String", "String")
            FieldType.UUID -> KotlinType("String", "String")
            FieldType.BLOB -> KotlinType("ByteArray", "ByteArray")
            FieldType.PASSWORD1WAY -> KotlinType("Password1wayModel", "MutablePassword1wayModel")
            FieldType.PASSWORD2WAY -> KotlinType("Password2wayModel", "MutablePassword2wayModel")
            FieldType.ALIAS -> KotlinType("NotYetSupported", "NotYetSupported")
            FieldType.ENUMERATION -> getEnumerationInfoKotlinType(getEnumerationInfoMeta(fieldMeta))
            FieldType.ASSOCIATION -> rootKotlinType
            FieldType.COMPOSITION -> getEntityInfoKotlinType(getEntityInfoMeta(fieldMeta, "composition"))
            null -> throw IllegalStateException("Field type not defined")
        }
    }

    private fun getMultiplicityKotlinType(multiplicity: Multiplicity, valueTypes: KotlinType): KotlinType =
        when (multiplicity) {
            Multiplicity.REQUIRED, Multiplicity.OPTIONAL -> valueTypes
            Multiplicity.SET -> KotlinType(
                "Iterable<${valueTypes.interfaceType}>",
                "MutableIterable<${valueTypes.mutableClassType}>"
            )
        }

    private fun getEnumerationInfoKotlinType(enumerationInfoMeta: EntityModel): KotlinType {
        val packageName = getSingleString(enumerationInfoMeta, "package").treeWareToKotlinPackageName()
        val enumerationName = getSingleString(enumerationInfoMeta, "name").snakeCaseToUpperCamelCase()
        val enumerationKotlinType = "$packageName.$enumerationName"
        return KotlinType(enumerationKotlinType, enumerationKotlinType)
    }

    private data class KotlinType(val interfaceType: String, val mutableClassType: String)

    private fun getEntityInfoKotlinType(entityInfoMeta: EntityModel): KotlinType {
        val packageName = getSingleString(entityInfoMeta, "package").treeWareToKotlinPackageName()
        val entityName = getSingleString(entityInfoMeta, "entity").snakeCaseToUpperCamelCase()
        return KotlinType("$packageName.$entityName", "$packageName.Mutable$entityName")
    }

    private fun encodeField(
        leaderFieldMeta1: EntityModel,
        info: String,
        fieldNameTreeWare: String,
        fieldNameKotlin: String,
        fieldType: FieldType,
        fieldKotlinType: KotlinType,
        multiplicity: Multiplicity,
        valueKotlinType: KotlinType,
    ) {
        when (fieldType) {
            FieldType.BOOLEAN,
            FieldType.UINT8,
            FieldType.UINT16,
            FieldType.UINT32,
            FieldType.UINT64,
            FieldType.INT8,
            FieldType.INT16,
            FieldType.INT32,
            FieldType.INT64,
            FieldType.FLOAT,
            FieldType.DOUBLE,
            FieldType.BIG_INTEGER,
            FieldType.BIG_DECIMAL,
            FieldType.TIMESTAMP,
            FieldType.STRING,
            FieldType.UUID,
            FieldType.BLOB -> encodePrimitiveField(fieldNameTreeWare, fieldNameKotlin, fieldKotlinType)
            FieldType.PASSWORD1WAY,
            FieldType.PASSWORD2WAY -> encodePasswordField(fieldNameTreeWare, fieldNameKotlin, fieldKotlinType)
            FieldType.ALIAS -> {} // Aliases are not yet supported.
            FieldType.ENUMERATION -> encodeEnumerationField(fieldNameTreeWare, fieldNameKotlin, fieldKotlinType)
            FieldType.ASSOCIATION -> encodeAssociationField(fieldNameTreeWare, fieldNameKotlin, fieldKotlinType)
            FieldType.COMPOSITION ->
                if (multiplicity != Multiplicity.SET) encodeCompositionSingleField(
                    fieldNameTreeWare,
                    fieldNameKotlin,
                    fieldKotlinType
                )
                else encodeCompositionSetField(
                    leaderFieldMeta1,
                    info,
                    fieldNameTreeWare,
                    fieldNameKotlin,
                    fieldKotlinType,
                    valueKotlinType
                )
        }
    }

    private fun encodePrimitiveField(fieldNameTreeWare: String, fieldNameKotlin: String, fieldKotlinType: KotlinType) {
        entityMutableClassFile.appendLine(
            """
            |    override var $fieldNameKotlin: ${fieldKotlinType.mutableClassType}?
            |        get() {
            |            val singleField = this.getField("$fieldNameTreeWare") as? SingleFieldModel ?: return null
            |            val primitive = singleField.value as? PrimitiveModel ?: return null
            |            return primitive.value as ${fieldKotlinType.mutableClassType}
            |        }
            |        set(newValue) {
            |            if (newValue == null) {
            |                this.getField("$fieldNameTreeWare")?.detachFromParent()
            |                return
            |            }
            |            val singleField = this.getOrNewField("$fieldNameTreeWare") as MutableSingleFieldModel
            |            val primitive = singleField.getOrNewValue() as MutablePrimitiveModel
            |            primitive.setValue(newValue.toString())
            |        }
            """.trimMargin()
        )
    }

    private fun encodePasswordField(fieldNameTreeWare: String, fieldNameKotlin: String, fieldKotlinType: KotlinType) {
        entityMutableClassFile.appendLine(
            """
            |    override val $fieldNameKotlin: ${fieldKotlinType.mutableClassType}?
            |        get() {
            |            val singleField = this.getField("$fieldNameTreeWare") as? SingleFieldModel ?: return null
            |            return singleField.value as ${fieldKotlinType.mutableClassType}?
            |        }
            |    fun $fieldNameKotlin(configure: ${fieldKotlinType.mutableClassType}.() -> Unit) {
            |        val singleField = this.getOrNewField("$fieldNameTreeWare") as MutableSingleFieldModel
            |        val password = singleField.getOrNewValue() as ${fieldKotlinType.mutableClassType}
            |        password.configure()
            |    }
            """.trimMargin()
        )
    }

    private fun encodeEnumerationField(
        fieldNameTreeWare: String,
        fieldNameKotlin: String,
        fieldKotlinType: KotlinType
    ) {
        entityMutableClassFile.appendLine(
            """
            |    override var $fieldNameKotlin: ${fieldKotlinType.mutableClassType}?
            |        get() {
            |            val singleField = this.getField("$fieldNameTreeWare") as? SingleFieldModel ?: return null
            |            val enumeration = singleField.value as? EnumerationModel ?: return null
            |            return try {
            |                ${fieldKotlinType.mutableClassType}.valueOf(enumeration.value.uppercase())
            |            } catch (e: IllegalArgumentException) {
            |                null
            |            }
            |        }
            |        set(newValue) {
            |            if (newValue == null) {
            |                this.getField("$fieldNameTreeWare")?.detachFromParent()
            |                return
            |            }
            |            val singleField = this.getOrNewField("$fieldNameTreeWare") as MutableSingleFieldModel
            |            val enumeration = singleField.getOrNewValue() as MutableEnumerationModel
            |            enumeration.setValue(newValue.name.lowercase())
            |        }
            """.trimMargin()
        )
    }

    private fun encodeAssociationField(
        fieldNameTreeWare: String,
        fieldNameKotlin: String,
        fieldKotlinType: KotlinType
    ) {
        entityMutableClassFile.appendLine(
            """
            |    override val $fieldNameKotlin: ${fieldKotlinType.mutableClassType}?
            |        get() {
            |            val singleField = this.getField("$fieldNameTreeWare") as? SingleFieldModel ?: return null
            |            val association = singleField.value as? MutableAssociationModel ?: return null
            |            return association.value as ${rootKotlinType.mutableClassType}?
            |        }
            |    fun $fieldNameKotlin(configure: ${fieldKotlinType.mutableClassType}.() -> Unit) {
            |        val singleField = this.getOrNewField("$fieldNameTreeWare") as MutableSingleFieldModel
            |        val association = singleField.getOrNewValue() as MutableAssociationModel
            |        val value = association.value as ${rootKotlinType.mutableClassType}
            |        value.configure()
            |    }
            """.trimMargin()
        )
    }

    private fun encodeCompositionSingleField(
        fieldNameTreeWare: String,
        fieldNameKotlin: String,
        fieldKotlinType: KotlinType
    ) {
        entityMutableClassFile.appendLine(
            """
            |    override val $fieldNameKotlin: ${fieldKotlinType.mutableClassType}?
            |        get() {
            |            val singleField = this.getField("$fieldNameTreeWare") as? SingleFieldModel ?: return null
            |            return singleField.value as? ${fieldKotlinType.mutableClassType}
            |        }
            |    fun $fieldNameKotlin(configure: ${fieldKotlinType.mutableClassType}.() -> Unit) {
            |        val singleField = this.getOrNewField("$fieldNameTreeWare") as MutableSingleFieldModel
            |        val entity = singleField.getOrNewValue() as ${fieldKotlinType.mutableClassType}
            |        entity.configure()
            |    }
            """.trimMargin()
        )
    }

    private fun encodeCompositionSetField(
        leaderFieldMeta1: EntityModel,
        info: String,
        fieldNameTreeWare: String,
        fieldNameKotlin: String,
        fieldKotlinType: KotlinType,
        valueKotlinType: KotlinType,
    ) {
        entityMutableClassFile.appendLine(
            """
            |    override val $fieldNameKotlin: ${fieldKotlinType.mutableClassType}?
            |        get() {
            |            val setField = this.getField("$fieldNameTreeWare") as? MutableSetFieldModel ?: return null
            |            @Suppress("UNCHECKED_CAST")
            |            return setField.values as? ${fieldKotlinType.mutableClassType}
            |        }
            """.trimMargin()
        )
        encodeSetFieldEntityGetter(leaderFieldMeta1, info, fieldNameTreeWare, fieldNameKotlin, valueKotlinType)
        entityMutableClassFile.appendLine(
            """
            |    fun $fieldNameKotlin(configure: ${valueKotlinType.mutableClassType}.SetBuilder.() -> Unit) {
            |        val setField = this.getOrNewField("$fieldNameTreeWare") as MutableSetFieldModel
            |        val setBuilder = ${valueKotlinType.mutableClassType}.SetBuilder(setField)
            |        setBuilder.configure()
            |    }
            """.trimMargin()
        )
    }

    /** Encode a function to get a particular entity from the set using key values.
     */
    private fun encodeSetFieldEntityGetter(
        leaderFieldMeta1: EntityModel,
        info: String,
        fieldNameTreeWare: String,
        fieldNameKotlin: String,
        valueKotlinType: KotlinType
    ) {
        if (info != "") {
            entityInterfaceFile.appendLine("    /** $info */")
            entityMutableClassFile.appendLine("    /** $info */")
        }
        // Encode keys as function parameters.
        val resolvedEntity = getMetaModelResolved(leaderFieldMeta1)?.compositionMeta ?: throw IllegalStateException(
            "Composition cannot be resolved"
        )
        val keyFieldsMeta = getKeyFieldsMeta(resolvedEntity)
        val keyParameterNamesAndTypes = getKeyParameterNames(keyFieldsMeta, null, true).joinToString(", ")
        val keyParameterNamesForModelId = getKeyParameterNames(keyFieldsMeta, null, false).joinToString(", ")
        entityInterfaceFile.append("    fun $fieldNameKotlin($keyParameterNamesAndTypes): ${valueKotlinType.interfaceType}?")
        entityMutableClassFile.appendLine(
            """
            |    override fun $fieldNameKotlin($keyParameterNamesAndTypes): ${valueKotlinType.mutableClassType}? {
            |        val keyValues = listOf<Any?>($keyParameterNamesForModelId)
            |        val elementModelId = EntityKeysModelId(keyValues)
            |        val setField = this.getField("$fieldNameTreeWare") as? MutableSetFieldModel ?: return null
            |        return setField.getValueMatching(elementModelId) as? ${valueKotlinType.mutableClassType}
            |    }
            """.trimMargin()
        )
    }

    private fun getKeyParameterNames(
        keyFieldsMeta: List<EntityModel>,
        prefix: String?,
        withTypes: Boolean
    ): List<String> = keyFieldsMeta.flatMap { keyFieldMeta ->
        val keyFieldName = getMetaName(keyFieldMeta).snakeCaseToLowerCamelCase()
        val keyFieldFlattenedName =
            if (prefix == null) keyFieldName else "$prefix${keyFieldName.replaceFirstChar(Char::uppercase)}"
        if (isCompositionFieldMeta(keyFieldMeta)) {
            val childResolvedEntity = getMetaModelResolved(keyFieldMeta)?.compositionMeta
                ?: throw IllegalStateException("Composite key field composition has not been resolved")
            val childKeyFieldsMeta = getKeyFieldsMeta(childResolvedEntity)
            getKeyParameterNames(childKeyFieldsMeta, keyFieldName, withTypes)
        } else if (withTypes) {
            val keyFieldType = getFieldKotlinType(keyFieldMeta)
            listOf("$keyFieldFlattenedName: ${keyFieldType.interfaceType}?")
        } else listOf(keyFieldFlattenedName)
    }

    // endregion

    // region State

    // MainModel state.
    private lateinit var dslMarkerName: String
    private lateinit var mainModelPackage: EncodeKotlinPackage
    private lateinit var mainModelInterfaceFile: EncodeKotlinElementFile
    private lateinit var mainModelMutableClassFile: EncodeKotlinElementFile

    // Package state.
    private lateinit var elementPackage: EncodeKotlinPackage

    // Enumeration state.
    private lateinit var enumerationFile: EncodeKotlinElementFile

    // Entity state.
    private lateinit var entityInterfaceFile: EncodeKotlinElementFile
    private lateinit var entityMutableClassFile: EncodeKotlinElementFile
    private lateinit var entityGetCompositionFactoryMethod: StringBuilder

    // endregion
}