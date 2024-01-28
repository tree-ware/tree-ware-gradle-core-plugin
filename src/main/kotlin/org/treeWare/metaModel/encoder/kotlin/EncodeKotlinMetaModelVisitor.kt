package org.treeWare.metaModel.encoder.kotlin

import okio.FileSystem
import okio.Path.Companion.toPath
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

    init {
        resetMainModelState()
        resetPackageState()
        resetEnumerationState()
        resetEntityState()
    }

    // region Leader1MetaModelVisitor methods

    override fun visitMainMeta(leaderMainMeta1: MainModel): TraversalAction {
        val treeWarePackageName = "" // TODO add support for package_name for main-model
        initializeMainModelPackageState(treeWarePackageName)
        val kotlinMetaModelConstant = writeKotlinMetaModelFile(leaderMainMeta1)
        startKotlinMainModelFiles(leaderMainMeta1, kotlinMetaModelConstant)
        return TraversalAction.CONTINUE
    }

    override fun leaveMainMeta(leaderMainMeta1: MainModel) {
        endKotlinMainModelFiles()
    }

    override fun visitPackageMeta(leaderPackageMeta1: EntityModel): TraversalAction {
        val treeWarePackageName = getMetaName(leaderPackageMeta1)
        initializePackageState(treeWarePackageName)
        return TraversalAction.CONTINUE
    }

    override fun leavePackageMeta(leaderPackageMeta1: EntityModel) {
        resetPackageState()
    }

    override fun visitEnumerationMeta(leaderEnumerationMeta1: EntityModel): TraversalAction {
        enumerationName = getMetaName(leaderEnumerationMeta1).snakeCaseToUpperCamelCase()
        enumerationContents.appendLine("enum class $enumerationName(val number: UInt) {")
        return TraversalAction.CONTINUE
    }

    override fun leaveEnumerationMeta(leaderEnumerationMeta1: EntityModel) {
        enumerationContents.append("}")
        writeFile(packageDirectory, enumerationName, enumerationImports, enumerationContents)
        resetEnumerationState()
    }

    override fun visitEnumerationValueMeta(leaderEnumerationValueMeta1: EntityModel): TraversalAction {
        val name = getMetaName(leaderEnumerationValueMeta1).uppercase()
        val number = getMetaNumber(leaderEnumerationValueMeta1)
        enumerationContents.appendLine("    $name(${number}u),")
        return TraversalAction.CONTINUE
    }

    override fun leaveEnumerationValueMeta(leaderEnumerationValueMeta1: EntityModel) {
        // Nothing to do.
    }

    override fun visitEntityMeta(leaderEntityMeta1: EntityModel): TraversalAction {
        val entityName = getMetaName(leaderEntityMeta1)
        // Start the interface contents.
        interfaceName = entityName.snakeCaseToUpperCamelCase()
        interfaceContents.appendLine("interface $interfaceName : EntityModel {")
        // Start the mutable-class contents.
        mutableClassName = "Mutable$interfaceName"
        mutableClassContents.appendLine(
            """
            |class $mutableClassName(
            |    meta: EntityModel,
            |    parent: MutableFieldModel
            |) : $interfaceName, MutableEntityModel(meta, parent) {
            """.trimMargin()
        )
        return TraversalAction.CONTINUE
    }

    override fun leaveEntityMeta(leaderEntityMeta1: EntityModel) {
        interfaceContents.append("}")
        writeFile(packageDirectory, interfaceName, interfaceImports, interfaceContents)
        mutableClassContents.append("}")
        writeFile(packageDirectory, mutableClassName, mutableClassImports, mutableClassContents)
        resetEntityState()
    }

    override fun visitFieldMeta(leaderFieldMeta1: EntityModel): TraversalAction {
        val name = getMetaName(leaderFieldMeta1).snakeCaseToLowerCamelCase()
        val info = getMetaInfo(leaderFieldMeta1)?.trim() ?: ""
        val valueType = getFieldKotlinType(leaderFieldMeta1)
        val multiplicity = getMultiplicityMeta(leaderFieldMeta1)
        val fieldType = when (multiplicity) {
            Multiplicity.REQUIRED, Multiplicity.OPTIONAL -> valueType
            Multiplicity.LIST, Multiplicity.SET -> "Iterable<$valueType>"
        }
        interfaceContents.appendLine()
        mutableClassContents.appendLine()
        if (info != "") {
            interfaceContents.appendLine("    /** $info */")
            mutableClassContents.appendLine("    /** $info */")
        }
        interfaceContents.appendLine("    val $name: $fieldType?")
        mutableClassContents.appendLine("    override val $name: $fieldType? get() = null")
        if (multiplicity == Multiplicity.SET) {
            // Encode a function to get a particular entity from the set.
            if (info != "") {
                interfaceContents.appendLine("    /** $info */")
                mutableClassContents.appendLine("    /** $info */")
            }
            interfaceContents.append("    fun $name(")
            mutableClassContents.append("    override fun $name(")
            // Encode keys as function parameters.
            val resolvedEntity = getMetaModelResolved(leaderFieldMeta1)?.compositionMeta ?: throw IllegalStateException(
                "Composition cannot be resolved"
            )
            getKeyFieldsMeta(resolvedEntity).forEachIndexed { index, keyFieldMeta ->
                val keyFieldName = getMetaName(keyFieldMeta).snakeCaseToLowerCamelCase()
                val keyFieldType = getFieldKotlinType(keyFieldMeta)
                if (index != 0) {
                    interfaceContents.append(", ")
                    mutableClassContents.append(", ")
                }
                interfaceContents.append("$keyFieldName: $keyFieldType?")
                mutableClassContents.append("$keyFieldName: $keyFieldType?")
            }
            interfaceContents.appendLine("): $valueType?")
            mutableClassContents.appendLine("): $valueType? = null")
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

        writeFile(mainModelPackageDirectory, fileName, imports, contents)
        return kotlinMetaModelConstant
    }

    private fun startKotlinMainModelFiles(mainMeta: MainModel, kotlinMetaModelConstant: String) {
        mainModelInterfaceName = getMainMetaName(mainMeta).snakeCaseToUpperCamelCase()
        mainModelInterfaceContents.appendLine("interface $mainModelInterfaceName : MainModel {")
        mainModelMutableClassName = "Mutable$mainModelInterfaceName"
        mainModelMutableClassContents.appendLine("class $mainModelMutableClassName : $mainModelInterfaceName, MutableMainModel($kotlinMetaModelConstant) {")
    }

    private fun endKotlinMainModelFiles() {
        mainModelInterfaceContents.append("}")
        writeFile(
            mainModelPackageDirectory,
            mainModelInterfaceName,
            mainModelInterfaceImports,
            mainModelInterfaceContents
        )
        mainModelMutableClassContents.append("}")
        writeFile(
            mainModelPackageDirectory,
            mainModelMutableClassName,
            mainModelMutableClassImports,
            mainModelMutableClassContents
        )
        resetMainModelState()
    }

    private fun writeFile(directoryName: String, baseFilename: String, imports: Set<String>, contents: StringBuilder) {
        val file = "$directoryName/$baseFilename.kt"
        FileSystem.SYSTEM.write(file.toPath()) {
            // Write the package clause.
            if (packageName.isNotEmpty()) this.writeUtf8("package ").writeUtf8(packageName).writeUtf8("\n\n")
            // Write the imports in sorted order.
            imports.sorted().forEach { this.writeUtf8("import ").writeUtf8(it).writeUtf8("\n") }
            if (imports.isNotEmpty()) this.writeUtf8("\n")
            // Write the main contents.
            this.writeUtf8(contents.toString())
        }
    }

    private fun getFieldKotlinType(fieldMeta: EntityModel): String {
        return when (getFieldTypeMeta(fieldMeta)) {
            FieldType.BOOLEAN -> "Boolean"
            FieldType.UINT8 -> "UByte"
            FieldType.UINT16 -> "UShort"
            FieldType.UINT32 -> "UInt"
            FieldType.UINT64 -> "ULong"
            FieldType.INT8 -> "Byte"
            FieldType.INT16 -> "Short"
            FieldType.INT32 -> "Int"
            FieldType.INT64 -> "Long"
            FieldType.FLOAT -> "Float"
            FieldType.DOUBLE -> "Double"
            FieldType.BIG_INTEGER -> "java.math.BigInteger"
            FieldType.BIG_DECIMAL -> "java.math.BigDecimal"
            FieldType.TIMESTAMP -> "ULong"
            FieldType.STRING -> "String"
            FieldType.UUID -> "String"
            FieldType.BLOB -> "ByteArray"
            FieldType.PASSWORD1WAY -> "Password1wayModel"
            FieldType.PASSWORD2WAY -> "Password2wayModel"
            FieldType.ALIAS -> "NotYetSupported"
            FieldType.ENUMERATION -> getEnumerationInfoKotlinType(getEnumerationInfoMeta(fieldMeta))
            FieldType.ASSOCIATION -> getEntityInfoKotlinType(getEntityInfoMeta(fieldMeta, "association"))
            FieldType.COMPOSITION -> getEntityInfoKotlinType(getEntityInfoMeta(fieldMeta, "composition"))
            null -> throw IllegalStateException("Field type not defined")
        }
    }

    private fun getEnumerationInfoKotlinType(enumerationInfoMeta: EntityModel): String {
        val packageName = getSingleString(enumerationInfoMeta, "package").treeWareToKotlinPackageName()
        val enumerationName = getSingleString(enumerationInfoMeta, "name").snakeCaseToUpperCamelCase()
        return "$packageName.$enumerationName"
    }

    private fun getEntityInfoKotlinType(entityInfoMeta: EntityModel): String {
        val packageName = getSingleString(entityInfoMeta, "package").treeWareToKotlinPackageName()
        val entityName = getSingleString(entityInfoMeta, "entity").snakeCaseToUpperCamelCase()
        return "$packageName.$entityName"
    }

    // endregion

    // region State

    // MainModel state.
    private lateinit var mainModelPackageName: String
    private lateinit var mainModelPackageDirectory: String
    private lateinit var mainModelInterfaceName: String
    private lateinit var mainModelInterfaceImports: MutableSet<String>
    private lateinit var mainModelInterfaceContents: StringBuilder
    private lateinit var mainModelMutableClassName: String
    private lateinit var mainModelMutableClassImports: MutableSet<String>
    private lateinit var mainModelMutableClassContents: StringBuilder

    private fun initializeMainModelPackageState(treeWarePackageName: String) {
        mainModelPackageName = treeWarePackageName.treeWareToKotlinPackageName()
        mainModelPackageDirectory = "$kotlinDirectoryPath/${mainModelPackageName.replace(".", "/")}"
        FileSystem.SYSTEM.createDirectories(mainModelPackageDirectory.toPath())
    }

    private fun resetMainModelState() {
        mainModelPackageName = ""
        mainModelPackageDirectory = ""

        mainModelInterfaceName = ""
        mainModelInterfaceImports = mutableSetOf("org.treeWare.model.core.*")
        mainModelInterfaceContents = StringBuilder()

        mainModelMutableClassName = ""
        mainModelMutableClassImports = mutableSetOf("org.treeWare.model.core.*")
        mainModelMutableClassContents = StringBuilder()
    }

    // Package state.
    private lateinit var packageName: String
    private lateinit var packageDirectory: String

    private fun initializePackageState(treeWarePackageName: String) {
        packageName = treeWarePackageName.treeWareToKotlinPackageName()
        packageDirectory = "$kotlinDirectoryPath/${packageName.replace(".", "/")}"
        FileSystem.SYSTEM.createDirectories(packageDirectory.toPath())
    }

    private fun resetPackageState() {
        packageName = ""
        packageDirectory = ""
    }

    // Enumeration state.
    private lateinit var enumerationName: String
    private lateinit var enumerationImports: MutableSet<String>
    private lateinit var enumerationContents: StringBuilder

    private fun resetEnumerationState() {
        enumerationName = ""
        enumerationImports = mutableSetOf()
        enumerationContents = StringBuilder()
    }

    // Entity state.
    private lateinit var interfaceName: String
    private lateinit var interfaceImports: MutableSet<String>
    private lateinit var interfaceContents: StringBuilder
    private lateinit var mutableClassName: String
    private lateinit var mutableClassImports: MutableSet<String>
    private lateinit var mutableClassContents: StringBuilder

    private fun resetEntityState() {
        interfaceName = ""
        interfaceImports = mutableSetOf("org.treeWare.model.core.*")
        interfaceContents = StringBuilder()

        mutableClassName = ""
        mutableClassImports = mutableSetOf("org.treeWare.model.core.*")
        mutableClassContents = StringBuilder()
    }

    // endregion
}

private fun String.treeWareToKotlinPackageName(): String =
    this.split(".").joinToString(".") { it.snakeCaseToLowerCamelCase() }
