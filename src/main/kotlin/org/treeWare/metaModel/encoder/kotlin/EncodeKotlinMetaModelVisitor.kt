package org.treeWare.metaModel.encoder.kotlin

import org.treeWare.metaModel.*
import org.treeWare.metaModel.encoder.util.snakeCaseToLowerCamelCase
import org.treeWare.metaModel.encoder.util.snakeCaseToUpperCamelCase
import org.treeWare.metaModel.traversal.AbstractLeader1MetaModelVisitor
import org.treeWare.model.core.EntityModel
import org.treeWare.model.core.MainModel
import org.treeWare.model.core.getMetaModelResolved
import org.treeWare.model.core.getSingleString
import org.treeWare.model.traversal.TraversalAction

class EncodeKotlinMetaModelVisitor(
    private val metaModelFilePaths: List<String>,
    private val kotlinDirectoryPath: String
) : AbstractLeader1MetaModelVisitor<TraversalAction>(TraversalAction.CONTINUE) {
    // TODO(deepak-nulu): remove the abstract base class to ensure all elements are encoded in Kotlin.

    private lateinit var rootKotlinType: KotlinModelTypes

    // region Leader1MetaModelVisitor methods

    override fun visitMainMeta(leaderMainMeta1: MainModel): TraversalAction {
        val treeWarePackageName = "" // TODO add support for package_name for main-model
        mainModelPackage = EncodeKotlinPackage(kotlinDirectoryPath, treeWarePackageName)
        val kotlinMetaModelConstant = writeKotlinMetaModelFile(leaderMainMeta1)
        startKotlinMainModelFiles(leaderMainMeta1, kotlinMetaModelConstant)
        return TraversalAction.CONTINUE
    }

    override fun leaveMainMeta(leaderMainMeta1: MainModel) {
        endKotlinMainModelFiles()
    }

    override fun visitRootMeta(leaderRootMeta1: EntityModel): TraversalAction {
        val types = getEntityInfoKotlinType(getEntityInfoMeta(leaderRootMeta1, "composition"))
        mainModelInterfaceFile.append("    val modelRoot: ").append(types.interfaceType).appendLine("?")
        mainModelMutableClassFile.append("    override val modelRoot: ").append(types.mutableClassType)
            .append("? get() = root as ").append(types.mutableClassType).appendLine("?")
        return TraversalAction.CONTINUE
    }

    override fun leaveRootMeta(leaderRootMeta1: EntityModel) {
        // Nothing to do.
    }

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
        entityInterfaceFile = EncodeKotlinElementFile(elementPackage, entityInterfaceName)
        entityInterfaceFile.import("org.treeWare.model.core.*")
        entityInterfaceFile.appendLine("interface $entityInterfaceName : EntityModel {")

        val entityMutableClassName = "Mutable$entityInterfaceName"
        entityMutableClassFile = EncodeKotlinElementFile(elementPackage, entityMutableClassName)
        entityMutableClassFile.import("org.treeWare.model.core.*")
        entityMutableClassFile.appendLine(
            """
            |class $entityMutableClassName(
            |    meta: EntityModel,
            |    parent: MutableFieldModel
            |) : $entityInterfaceName, MutableEntityModel(meta, parent) {
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
        entityInterfaceFile.append("}")
        entityInterfaceFile.write()

        entityGetCompositionFactoryMethod.appendLine(
            """
            |            // New fields might have been added to the meta-model after this code was generated.
            |            // Handle those new fields by calling super.
            |            else -> super.getCompositionFactory(fieldName, fieldMeta)
            |        }
            """.trimMargin()
        )
        entityMutableClassFile.appendLine("").append(entityGetCompositionFactoryMethod.toString())
        entityMutableClassFile.append("}")
        entityMutableClassFile.write()
    }

    override fun visitFieldMeta(leaderFieldMeta1: EntityModel): TraversalAction {
        val fieldNameTreeWare = getMetaName(leaderFieldMeta1)
        val fieldNameKotlin = fieldNameTreeWare.snakeCaseToLowerCamelCase()
        val info = getMetaInfo(leaderFieldMeta1)?.trim() ?: ""
        val fieldType = getFieldTypeMeta(leaderFieldMeta1) ?: throw IllegalStateException("Field type not defined")
        val valueTypes = getFieldKotlinType(leaderFieldMeta1)
        val multiplicity = getMultiplicityMeta(leaderFieldMeta1)
        val fieldClasses = getMultiplicityKotlinType(multiplicity, valueTypes)
        entityInterfaceFile.appendLine("")
        entityMutableClassFile.appendLine("")
        if (info != "") {
            entityInterfaceFile.appendLine("    /** $info */")
            entityMutableClassFile.appendLine("    /** $info */")
        }
        entityInterfaceFile.appendLine("    val $fieldNameKotlin: ${fieldClasses.interfaceType}?")
        encodeFieldGetter(fieldNameTreeWare, fieldNameKotlin, fieldType, fieldClasses, multiplicity)
        if (fieldType == FieldType.COMPOSITION) entityGetCompositionFactoryMethod.appendLine(
            """            "$fieldNameTreeWare" -> ${valueTypes.mutableClassType}.fieldValueFactory"""
        )
        if (multiplicity == Multiplicity.SET) {
            encodeSetFieldEntityGetter(leaderFieldMeta1, info, fieldNameTreeWare, fieldNameKotlin, valueTypes)
        }
        return TraversalAction.CONTINUE
    }

    override fun leaveFieldMeta(leaderFieldMeta1: EntityModel) {
        // Nothing to do.
    }

    // endregion

    // region Helper methods

    private fun writeKotlinMetaModelFile(mainMeta: MainModel): String {
        val mainMetaName = getMainMetaName(mainMeta)
        val fileName = mainMetaName.snakeCaseToUpperCamelCase() + "MetaModel"
        val file = EncodeKotlinElementFile(mainModelPackage, fileName)
        file.import("org.treeWare.metaModel.newMetaModelFromJsonFiles")

        val metaModelFilesConstant = mainMetaName.uppercase() + "_META_MODEL_FILES"
        file.append("val ").append(metaModelFilesConstant).appendLine(" = listOf(")
        this.metaModelFilePaths.sorted().forEach { absolutePath ->
            val splits = absolutePath.split("resources/")
            val relativePath = if (splits.size > 1) splits[1] else splits[0]
            file.append("    \"").append(relativePath).appendLine("\",")
        }
        file.appendLine(")")
        file.appendLine("")

        val kotlinMetaModelConstant = mainMetaName.snakeCaseToLowerCamelCase() + "MetaModel"
        file.append("val ").append(kotlinMetaModelConstant).appendLine(" = newMetaModelFromJsonFiles(")
        file.append("    ").append(metaModelFilesConstant)
        file.appendLine(", false, null, null, ::rootEntityFactory, emptyList(), true")
        file.append(").metaModel ?: throw IllegalStateException(\"Meta-model has validation errors\")")

        file.write()
        return kotlinMetaModelConstant
    }

    private fun startKotlinMainModelFiles(mainMeta: MainModel, kotlinMetaModelConstant: String) {
        val mainModelInterfaceName = getMainMetaName(mainMeta).snakeCaseToUpperCamelCase()
        mainModelInterfaceFile = EncodeKotlinElementFile(mainModelPackage, mainModelInterfaceName)
        mainModelInterfaceFile.import("org.treeWare.model.core.*")
        mainModelInterfaceFile.appendLine("interface $mainModelInterfaceName : MainModel {")

        val rootMeta = getRootMeta(mainMeta)
        rootKotlinType = getEntityInfoKotlinType(getEntityInfoMeta(rootMeta, "composition"))

        val mainModelMutableClassName = "Mutable$mainModelInterfaceName"
        mainModelMutableClassFile = EncodeKotlinElementFile(mainModelPackage, mainModelMutableClassName)
        mainModelMutableClassFile.import("org.treeWare.model.core.*")
        // rootEntityFactory() function.
        mainModelMutableClassFile.appendLine(
            """
            |fun rootEntityFactory(rootMeta: EntityModel, parent: MutableFieldModel) =
            |    ${rootKotlinType.mutableClassType}(rootMeta, parent)
            |
            """.trimMargin()
        )
        // MutableMainModel subclass.
        mainModelMutableClassFile.appendLine(
            """
            |class $mainModelMutableClassName : $mainModelInterfaceName, MutableMainModel(
            |    $kotlinMetaModelConstant, ${rootKotlinType.mutableClassType}.fieldValueFactory
            |) {
            """.trimMargin()
        )
    }

    private fun endKotlinMainModelFiles() {
        mainModelInterfaceFile.append("}")
        mainModelInterfaceFile.write()

        mainModelMutableClassFile.append("}")
        mainModelMutableClassFile.write()
    }

    private fun getFieldKotlinType(fieldMeta: EntityModel): KotlinModelTypes {
        return when (getFieldTypeMeta(fieldMeta)) {
            FieldType.BOOLEAN -> KotlinModelTypes("Boolean", "Boolean")
            FieldType.UINT8 -> KotlinModelTypes("UByte", "UByte")
            FieldType.UINT16 -> KotlinModelTypes("UShort", "UShort")
            FieldType.UINT32 -> KotlinModelTypes("UInt", "UInt")
            FieldType.UINT64 -> KotlinModelTypes("ULong", "ULong")
            FieldType.INT8 -> KotlinModelTypes("Byte", "Byte")
            FieldType.INT16 -> KotlinModelTypes("Short", "Short")
            FieldType.INT32 -> KotlinModelTypes("Int", "Int")
            FieldType.INT64 -> KotlinModelTypes("Long", "Long")
            FieldType.FLOAT -> KotlinModelTypes("Float", "Float")
            FieldType.DOUBLE -> KotlinModelTypes("Double", "Double")
            FieldType.BIG_INTEGER -> KotlinModelTypes("java.math.BigInteger", "java.math.BigInteger")
            FieldType.BIG_DECIMAL -> KotlinModelTypes("java.math.BigDecimal", "java.math.BigDecimal")
            FieldType.TIMESTAMP -> KotlinModelTypes("ULong", "ULong")
            FieldType.STRING -> KotlinModelTypes("String", "String")
            FieldType.UUID -> KotlinModelTypes("String", "String")
            FieldType.BLOB -> KotlinModelTypes("ByteArray", "ByteArray")
            FieldType.PASSWORD1WAY -> KotlinModelTypes("Password1wayModel", "MutablePassword1wayModel")
            FieldType.PASSWORD2WAY -> KotlinModelTypes("Password2wayModel", "MutablePassword2wayModel")
            FieldType.ALIAS -> KotlinModelTypes("NotYetSupported", "NotYetSupported")
            FieldType.ENUMERATION -> getEnumerationInfoKotlinType(getEnumerationInfoMeta(fieldMeta))
            FieldType.ASSOCIATION -> rootKotlinType
            FieldType.COMPOSITION -> getEntityInfoKotlinType(getEntityInfoMeta(fieldMeta, "composition"))
            null -> throw IllegalStateException("Field type not defined")
        }
    }

    private fun getMultiplicityKotlinType(multiplicity: Multiplicity, valueTypes: KotlinModelTypes): KotlinModelTypes =
        when (multiplicity) {
            Multiplicity.REQUIRED, Multiplicity.OPTIONAL -> valueTypes
            Multiplicity.LIST, Multiplicity.SET -> KotlinModelTypes(
                "Iterable<${valueTypes.interfaceType}>",
                "MutableIterable<${valueTypes.mutableClassType}>"
            )
        }

    private fun getEnumerationInfoKotlinType(enumerationInfoMeta: EntityModel): KotlinModelTypes {
        val packageName = getSingleString(enumerationInfoMeta, "package").treeWareToKotlinPackageName()
        val enumerationName = getSingleString(enumerationInfoMeta, "name").snakeCaseToUpperCamelCase()
        val enumerationKotlinType = "$packageName.$enumerationName"
        return KotlinModelTypes(enumerationKotlinType, enumerationKotlinType)
    }

    private data class KotlinModelTypes(val interfaceType: String, val mutableClassType: String)

    private fun getEntityInfoKotlinType(entityInfoMeta: EntityModel): KotlinModelTypes {
        val packageName = getSingleString(entityInfoMeta, "package").treeWareToKotlinPackageName()
        val entityName = getSingleString(entityInfoMeta, "entity").snakeCaseToUpperCamelCase()
        return KotlinModelTypes("$packageName.$entityName", "$packageName.Mutable$entityName")
    }

    private fun encodeFieldGetter(
        fieldNameTreeWare: String,
        fieldNameKotlin: String,
        fieldType: FieldType,
        fieldClasses: KotlinModelTypes,
        multiplicity: Multiplicity
    ) {
        entityMutableClassFile.appendLine("    override val $fieldNameKotlin: ${fieldClasses.mutableClassType}? get() {")
        when (multiplicity) {
            Multiplicity.REQUIRED, Multiplicity.OPTIONAL -> encodeSingleFieldGetter(
                fieldNameTreeWare,
                fieldType,
                fieldClasses
            )
            Multiplicity.SET -> encodeSetFieldGetter(fieldNameTreeWare, fieldClasses)
            Multiplicity.LIST -> entityMutableClassFile.appendLine("""        TODO("Lists are getting dropped")""")
            else -> entityMutableClassFile.appendLine("        return null")
        }
        entityMutableClassFile.appendLine("    }")
    }

    private fun encodeSingleFieldGetter(
        fieldNameTreeWare: String,
        fieldType: FieldType,
        fieldClasses: KotlinModelTypes
    ) {
        entityMutableClassFile.appendLine("""        val singleField = this.getField("$fieldNameTreeWare") as? SingleFieldModel ?: return null""")
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
            FieldType.BLOB -> {
                entityMutableClassFile.appendLine("""        val primitive = singleField.value as? PrimitiveModel ?: return null""")
                entityMutableClassFile.appendLine("""        return primitive.value as ${fieldClasses.mutableClassType}?""")
            }
            FieldType.PASSWORD1WAY,
            FieldType.PASSWORD2WAY -> {
                entityMutableClassFile.appendLine("""        return singleField.value as ${fieldClasses.mutableClassType}?""")
            }
            FieldType.ALIAS -> entityMutableClassFile.appendLine("""        TODO()""")
            FieldType.ENUMERATION -> {
                entityMutableClassFile.appendLine(
                    """
                    |        val enumeration = singleField.value as? EnumerationModel ?: return null
                    |        return try {
                    |            ${fieldClasses.mutableClassType}.valueOf(enumeration.value.uppercase())
                    |        } catch (e: IllegalArgumentException) {
                    |            null
                    |        }
                    """.trimMargin()
                )
            }
            FieldType.ASSOCIATION -> {
                entityMutableClassFile.appendLine(
                    """
                    |        val association = singleField.value as? MutableAssociationModel ?: return null
                    |        return association.value as ${rootKotlinType.mutableClassType}?
                    """.trimMargin()
                )
            }
            FieldType.COMPOSITION -> {
                entityMutableClassFile.appendLine("""        return singleField.value as? ${fieldClasses.mutableClassType}""")
            }
        }
    }

    private fun encodeSetFieldGetter(fieldNameTreeWare: String, fieldClasses: KotlinModelTypes) {
        entityMutableClassFile.appendLine("""        val setField = this.getField("$fieldNameTreeWare") as? MutableSetFieldModel ?: return null""")
        entityMutableClassFile.appendLine("""        @Suppress("UNCHECKED_CAST")""")
        entityMutableClassFile.appendLine("""        return setField.values as? ${fieldClasses.mutableClassType}""")
    }

    /** Encode a function to get a particular entity from the set using key values.
     */
    private fun encodeSetFieldEntityGetter(
        leaderFieldMeta1: EntityModel,
        info: String,
        fieldNameTreeWare: String,
        fieldNameKotlin: String,
        valueType: KotlinModelTypes
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
        entityInterfaceFile.append("    fun $fieldNameKotlin($keyParameterNamesAndTypes): ${valueType.interfaceType}?")
        entityMutableClassFile.appendLine(
            """
            |    override fun $fieldNameKotlin($keyParameterNamesAndTypes): ${valueType.mutableClassType}? {
            |        val keyValues = listOf<Any?>($keyParameterNamesForModelId)
            |        val elementModelId = EntityKeysModelId(keyValues)
            |        val setField = this.getField("$fieldNameTreeWare") as? MutableSetFieldModel ?: return null
            |        return setField.getValueMatching(elementModelId) as? ${valueType.mutableClassType}
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