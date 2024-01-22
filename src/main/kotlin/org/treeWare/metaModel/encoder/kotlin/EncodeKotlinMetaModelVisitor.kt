package org.treeWare.metaModel.encoder.kotlin

import okio.FileSystem
import okio.Path.Companion.toPath
import org.treeWare.metaModel.*
import org.treeWare.metaModel.encoder.util.snakeCaseToLowerCamelCase
import org.treeWare.metaModel.encoder.util.snakeCaseToUpperCamelCase
import org.treeWare.metaModel.traversal.AbstractLeader1MetaModelVisitor
import org.treeWare.model.core.EntityModel
import org.treeWare.model.core.getMetaModelResolved
import org.treeWare.model.core.getSingleString
import org.treeWare.model.traversal.TraversalAction

class EncodeKotlinMetaModelVisitor(
    private val kotlinDirectoryPath: String
) : AbstractLeader1MetaModelVisitor<TraversalAction>(TraversalAction.CONTINUE) {
    // TODO(deepak-nulu): remove the abstract base class to ensure all elements are encoded in Kotlin.

    init {
        resetPackageState()
        resetEnumerationState()
        resetEntityState()
    }

    // region Leader1MetaModelVisitor methods

    override fun visitPackageMeta(leaderPackageMeta1: EntityModel): TraversalAction {
        packageName = getMetaName(leaderPackageMeta1).treeWareToKotlinPackageName()
        packageDirectory = "$kotlinDirectoryPath/${packageName.replace(".", "/")}"
        FileSystem.SYSTEM.createDirectories(packageDirectory.toPath())
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
        writeFile(enumerationName, enumerationImports, enumerationContents)
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
        writeFile(interfaceName, interfaceImports, interfaceContents)
        mutableClassContents.append("}")
        writeFile(mutableClassName, mutableClassImports, mutableClassContents)
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

    private fun writeFile(baseFilename: String, imports: Set<String>, contents: StringBuilder) {
        val file = "$packageDirectory/$baseFilename.kt"
        FileSystem.SYSTEM.write(file.toPath()) {
            // Write the package clause.
            this.writeUtf8("package ").writeUtf8(packageName).writeUtf8("\n")
            // Write the imports in sorted order.
            if (imports.isNotEmpty()) this.writeUtf8("\n")
            imports.sorted().forEach { this.writeUtf8("import ").writeUtf8(it).writeUtf8("\n") }
            // Write the main contents.
            if (contents.isNotEmpty()) this.writeUtf8("\n")
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

    // Package state.
    private lateinit var packageName: String
    private lateinit var packageDirectory: String

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
