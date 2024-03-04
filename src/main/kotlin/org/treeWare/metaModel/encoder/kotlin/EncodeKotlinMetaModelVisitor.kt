package org.treeWare.metaModel.encoder.kotlin

import okio.FileSystem
import okio.Path.Companion.toPath
import org.treeWare.metaModel.*
import org.treeWare.metaModel.encoder.util.snakeCaseToLowerCamelCase
import org.treeWare.metaModel.encoder.util.snakeCaseToUpperCamelCase
import org.treeWare.metaModel.traversal.AbstractLeader1MetaModelVisitor
import org.treeWare.model.core.*
import org.treeWare.model.traversal.TraversalAction

class EncodeKotlinMetaModelVisitor(
    private val metaModelFilePaths: List<String>,
    private val kotlinDirectoryPath: String
) : AbstractLeader1MetaModelVisitor<TraversalAction>(TraversalAction.CONTINUE) {
    // TODO(deepak-nulu): remove the abstract base class to ensure all elements are encoded in Kotlin.

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
        enumerationFile.appendLine("enum class $enumerationName(val number: UInt) {")
        return TraversalAction.CONTINUE
    }

    override fun leaveEnumerationMeta(leaderEnumerationMeta1: EntityModel) {
        enumerationFile.append("}")
        enumerationFile.write()
    }

    override fun visitEnumerationValueMeta(leaderEnumerationValueMeta1: EntityModel): TraversalAction {
        val name = getMetaName(leaderEnumerationValueMeta1).uppercase()
        val number = getMetaNumber(leaderEnumerationValueMeta1)
        enumerationFile.appendLine("    $name(${number}u),")
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
            |    protected override fun getCompositionFactory(fieldName: String, fieldMeta: EntityModel): FieldValueFactory =
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
        val name = getMetaName(leaderFieldMeta1).snakeCaseToLowerCamelCase()
        val info = getMetaInfo(leaderFieldMeta1)?.trim() ?: ""
        val fieldType = getFieldTypeMeta(leaderFieldMeta1)
        val valueTypes = getFieldKotlinType(leaderFieldMeta1)
        val multiplicity = getMultiplicityMeta(leaderFieldMeta1)
        val fieldClasses = getMultiplicityKotlinType(multiplicity, valueTypes)
        entityInterfaceFile.appendLine("")
        entityMutableClassFile.appendLine("")
        if (info != "") {
            entityInterfaceFile.appendLine("    /** $info */")
            entityMutableClassFile.appendLine("    /** $info */")
        }
        entityInterfaceFile.appendLine("    val $name: ${fieldClasses.interfaceType}?")
        entityMutableClassFile.appendLine("    override val $name: ${fieldClasses.mutableClassType}? get() = null")
        if (fieldType == FieldType.COMPOSITION) entityGetCompositionFactoryMethod.appendLine(
            """            "$name" -> ${valueTypes.mutableClassType}.fieldValueFactory"""
        )
        if (multiplicity == Multiplicity.SET) {
            // Encode a function to get a particular entity from the set.
            if (info != "") {
                entityInterfaceFile.appendLine("    /** $info */")
                entityMutableClassFile.appendLine("    /** $info */")
            }
            entityInterfaceFile.append("    fun $name(")
            entityMutableClassFile.append("    override fun $name(")
            // Encode keys as function parameters.
            val resolvedEntity = getMetaModelResolved(leaderFieldMeta1)?.compositionMeta ?: throw IllegalStateException(
                "Composition cannot be resolved"
            )
            getKeyFieldsMeta(resolvedEntity).forEachIndexed { index, keyFieldMeta ->
                val keyFieldName = getMetaName(keyFieldMeta).snakeCaseToLowerCamelCase()
                val keyFieldType = getFieldKotlinType(keyFieldMeta)
                if (index != 0) {
                    entityInterfaceFile.append(", ")
                    entityMutableClassFile.append(", ")
                }
                entityInterfaceFile.append("$keyFieldName: ${keyFieldType.interfaceType}?")
                entityMutableClassFile.append("$keyFieldName: ${keyFieldType.interfaceType}?")
            }
            entityInterfaceFile.appendLine("): ${valueTypes.interfaceType}?")
            entityMutableClassFile.appendLine("): ${valueTypes.mutableClassType}? = null")
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
        val imports = setOf("org.treeWare.metaModel.newMetaModelFromJsonFiles")
        val contents = StringBuilder()

        val metaModelFilesConstant = mainMetaName.uppercase() + "_META_MODEL_FILES"
        contents.append("val ").append(metaModelFilesConstant).appendLine(" = listOf(")
        this.metaModelFilePaths.sorted().forEach { absolutePath ->
            val splits = absolutePath.split("resources/")
            val relativePath = if (splits.size > 1) splits[1] else splits[0]
            contents.append("    \"").append(relativePath).appendLine("\",")
        }
        contents.appendLine(")")
        contents.appendLine()

        val kotlinMetaModelConstant = mainMetaName.snakeCaseToLowerCamelCase() + "MetaModel"
        contents.append("val ").append(kotlinMetaModelConstant).appendLine(" = newMetaModelFromJsonFiles(")
        contents.append("    ").append(metaModelFilesConstant).appendLine(", false, null, null, emptyList(), true")
        contents.append(").metaModel ?: throw IllegalStateException(\"Meta-model has validation errors\")")

        writeFile(mainModelPackage, fileName, imports, contents)
        return kotlinMetaModelConstant
    }

    private fun startKotlinMainModelFiles(mainMeta: MainModel, kotlinMetaModelConstant: String) {
        val mainModelInterfaceName = getMainMetaName(mainMeta).snakeCaseToUpperCamelCase()
        mainModelInterfaceFile = EncodeKotlinElementFile(mainModelPackage, mainModelInterfaceName)
        mainModelInterfaceFile.import("org.treeWare.model.core.*")
        mainModelInterfaceFile.appendLine("interface $mainModelInterfaceName : MainModel {")

        val rootMeta = getRootMeta(mainMeta)
        val rootTypes = getEntityInfoKotlinType(getEntityInfoMeta(rootMeta, "composition"))

        val mainModelMutableClassName = "Mutable$mainModelInterfaceName"
        mainModelMutableClassFile = EncodeKotlinElementFile(mainModelPackage, mainModelMutableClassName)
        mainModelMutableClassFile.import("org.treeWare.model.core.*")
        mainModelMutableClassFile.appendLine(
            """
            |class $mainModelMutableClassName : $mainModelInterfaceName, MutableMainModel(
            |    $kotlinMetaModelConstant, ${rootTypes.mutableClassType}.fieldValueFactory
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

    private fun writeFile(
        elementPackage: EncodeKotlinPackage,
        baseFilename: String,
        imports: Set<String>,
        contents: StringBuilder
    ) {
        val file = "${elementPackage.directory}/$baseFilename.kt"
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
            FieldType.PASSWORD1WAY -> KotlinModelTypes("Password1wayModel", "Password1wayModel")
            FieldType.PASSWORD2WAY -> KotlinModelTypes("Password2wayModel", "Password2wayModel")
            FieldType.ALIAS -> KotlinModelTypes("NotYetSupported", "NotYetSupported")
            FieldType.ENUMERATION -> getEnumerationInfoKotlinType(getEnumerationInfoMeta(fieldMeta))
            FieldType.ASSOCIATION -> getEntityInfoKotlinType(getEntityInfoMeta(fieldMeta, "association"))
            FieldType.COMPOSITION -> getEntityInfoKotlinType(getEntityInfoMeta(fieldMeta, "composition"))
            null -> throw IllegalStateException("Field type not defined")
        }
    }

    private fun getMultiplicityKotlinType(multiplicity: Multiplicity, valueTypes: KotlinModelTypes): KotlinModelTypes =
        when (multiplicity) {
            Multiplicity.REQUIRED, Multiplicity.OPTIONAL -> valueTypes
            Multiplicity.LIST, Multiplicity.SET -> KotlinModelTypes(
                "Iterable<${valueTypes.interfaceType}>",
                "Iterable<${valueTypes.mutableClassType}>"
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