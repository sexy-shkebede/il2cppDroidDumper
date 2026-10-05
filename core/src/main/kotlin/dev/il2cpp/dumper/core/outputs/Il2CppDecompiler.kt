package dev.il2cpp.dumper.core.outputs

import dev.il2cpp.dumper.core.Config
import dev.il2cpp.dumper.core.DumpLogger
import dev.il2cpp.dumper.core.DumpProgress
import dev.il2cpp.dumper.core.extra.FieldEntry
import dev.il2cpp.dumper.core.extra.GenericInstanceEntry
import dev.il2cpp.dumper.core.extra.MethodEntry
import dev.il2cpp.dumper.core.extra.ParamEntry
import dev.il2cpp.dumper.core.extra.PropertyEntry
import dev.il2cpp.dumper.core.extra.TypeEntry
import dev.il2cpp.dumper.core.extra.TypeKind
import dev.il2cpp.dumper.core.il2cpp.Il2CppImageDefinition
import dev.il2cpp.dumper.core.il2cpp.Il2CppMethodDefinition
import dev.il2cpp.dumper.core.io.Utf8Writer
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.FIELD_ATTRIBUTE_ASSEMBLY
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.FIELD_ATTRIBUTE_FAMILY
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.FIELD_ATTRIBUTE_FAM_AND_ASSEM
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.FIELD_ATTRIBUTE_FAM_OR_ASSEM
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.FIELD_ATTRIBUTE_FIELD_ACCESS_MASK
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.FIELD_ATTRIBUTE_INIT_ONLY
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.FIELD_ATTRIBUTE_LITERAL
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.FIELD_ATTRIBUTE_PRIVATE
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.FIELD_ATTRIBUTE_PUBLIC
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.FIELD_ATTRIBUTE_STATIC
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.METHOD_ATTRIBUTE_ABSTRACT
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.METHOD_ATTRIBUTE_ASSEM
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.METHOD_ATTRIBUTE_FAMILY
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.METHOD_ATTRIBUTE_FAM_AND_ASSEM
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.METHOD_ATTRIBUTE_FAM_OR_ASSEM
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.METHOD_ATTRIBUTE_FINAL
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.METHOD_ATTRIBUTE_MEMBER_ACCESS_MASK
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.METHOD_ATTRIBUTE_NEW_SLOT
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.METHOD_ATTRIBUTE_PINVOKE_IMPL
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.METHOD_ATTRIBUTE_PRIVATE
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.METHOD_ATTRIBUTE_PUBLIC
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.METHOD_ATTRIBUTE_REUSE_SLOT
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.METHOD_ATTRIBUTE_STATIC
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.METHOD_ATTRIBUTE_VIRTUAL
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.METHOD_ATTRIBUTE_VTABLE_LAYOUT_MASK
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.PARAM_ATTRIBUTE_IN
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.PARAM_ATTRIBUTE_OUT
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.TYPE_ATTRIBUTE_ABSTRACT
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.TYPE_ATTRIBUTE_INTERFACE
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.TYPE_ATTRIBUTE_NESTED_ASSEMBLY
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.TYPE_ATTRIBUTE_NESTED_FAMILY
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.TYPE_ATTRIBUTE_NESTED_FAM_AND_ASSEM
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.TYPE_ATTRIBUTE_NESTED_FAM_OR_ASSEM
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.TYPE_ATTRIBUTE_NESTED_PRIVATE
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.TYPE_ATTRIBUTE_NESTED_PUBLIC
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.TYPE_ATTRIBUTE_NOT_PUBLIC
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.TYPE_ATTRIBUTE_PUBLIC
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.TYPE_ATTRIBUTE_SEALED
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.TYPE_ATTRIBUTE_SERIALIZABLE
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.TYPE_ATTRIBUTE_VISIBILITY_MASK
import dev.il2cpp.dumper.core.utils.CSharp
import dev.il2cpp.dumper.core.utils.CustomAttributeDataReader
import dev.il2cpp.dumper.core.utils.Il2CppExecutor
import dev.il2cpp.dumper.core.utils.hex
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.util.IdentityHashMap

/** Port of Il2CppDecompiler.cs (dump.cs writer) that can also build the structured [TypeEntry] index. */
class Il2CppDecompiler(private val executor: Il2CppExecutor, private val logger: DumpLogger = DumpLogger.NONE) {
    private val metadata = executor.metadata
    private val il2Cpp = executor.il2Cpp
    private val methodModifiers = IdentityHashMap<Il2CppMethodDefinition, String>()

    /** Collected when [collectIndex] is true. */
    val typeEntries = ArrayList<TypeEntry>()

    fun decompile(config: Config, outputDir: File, collectIndex: Boolean = false, progress: DumpProgress = DumpProgress.NONE) {
        val writer = Utf8Writer(FileOutputStream(File(outputDir, "dump.cs")))
        try {
            decompile(config, writer, collectIndex, progress)
        } finally {
            writer.close()
        }
    }

    fun decompile(config: Config, writer: Appendable, collectIndex: Boolean, progress: DumpProgress) {
        val imageDefs = metadata.imageDefs
        for (imageIndex in imageDefs.indices) {
            val imageDef = imageDefs[imageIndex]
            writer.append("// Image $imageIndex: ${metadata.getStringFromIndex(imageDef.nameIndex)} - ${imageDef.typeStart}\n")
        }
        val totalTypes = metadata.typeDefs.size.coerceAtLeast(1)
        var doneTypes = 0
        for ((imageIndex, imageDef) in imageDefs.withIndex()) {
            try {
                val imageName = metadata.getStringFromIndex(imageDef.nameIndex)
                val typeEnd = imageDef.typeStart + imageDef.typeCount
                var typeDefIndex = imageDef.typeStart
                while (typeDefIndex < typeEnd) {
                    if (doneTypes and 0xFF == 0) {
                        progress.checkCancelled()
                        progress.onProgress("dump.cs", doneTypes.toFloat() / totalTypes)
                    }
                    doneTypes++
                    dumpType(config, writer, imageDef, imageIndex, imageName, typeDefIndex, collectIndex)
                    typeDefIndex++
                }
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                logger.log("ERROR: Some errors in dumping")
                writer.append("/*")
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                writer.append(sw.toString().replace("\r\n", "\n"))
                writer.append("*/\n}\n")
            }
        }
    }

    private fun dumpType(
        config: Config, writer: Appendable, imageDef: Il2CppImageDefinition, imageIndex: Int,
        imageName: String, typeDefIndex: Int, collectIndex: Boolean,
    ) {
        val typeDef = metadata.typeDefs[typeDefIndex]
        val extends = ArrayList<String>()
        if (typeDef.parentIndex >= 0) {
            val parent = il2Cpp.types[typeDef.parentIndex]
            val parentName = executor.getTypeName(parent, false, false)
            if (!typeDef.isValueType && !typeDef.isEnum && parentName != "object") {
                extends.add(parentName)
            }
        }
        if (typeDef.interfacesCount > 0) {
            for (i in 0 until typeDef.interfacesCount) {
                val iface = il2Cpp.types[metadata.interfaceIndices[typeDef.interfacesStart + i]]
                extends.add(executor.getTypeName(iface, false, false))
            }
        }
        val namespace = metadata.getStringFromIndex(typeDef.namespaceIndex)
        writer.append("\n// Namespace: ").append(namespace).append("\n")
        var typeAttributes = ""
        if (config.dumpAttribute) {
            typeAttributes = getCustomAttribute(imageDef, typeDef.customAttributeIndex, typeDef.token)
            writer.append(typeAttributes)
        }
        if (config.dumpAttribute && (typeDef.flags and TYPE_ATTRIBUTE_SERIALIZABLE) != 0L) {
            writer.append("[Serializable]\n")
            typeAttributes += "[Serializable]\n"
        }
        val mods = StringBuilder()
        when (typeDef.flags and TYPE_ATTRIBUTE_VISIBILITY_MASK) {
            TYPE_ATTRIBUTE_PUBLIC, TYPE_ATTRIBUTE_NESTED_PUBLIC -> mods.append("public ")
            TYPE_ATTRIBUTE_NOT_PUBLIC, TYPE_ATTRIBUTE_NESTED_FAM_AND_ASSEM, TYPE_ATTRIBUTE_NESTED_ASSEMBLY -> mods.append("internal ")
            TYPE_ATTRIBUTE_NESTED_PRIVATE -> mods.append("private ")
            TYPE_ATTRIBUTE_NESTED_FAMILY -> mods.append("protected ")
            TYPE_ATTRIBUTE_NESTED_FAM_OR_ASSEM -> mods.append("protected internal ")
        }
        if ((typeDef.flags and TYPE_ATTRIBUTE_ABSTRACT) != 0L && (typeDef.flags and TYPE_ATTRIBUTE_SEALED) != 0L)
            mods.append("static ")
        else if ((typeDef.flags and TYPE_ATTRIBUTE_INTERFACE) == 0L && (typeDef.flags and TYPE_ATTRIBUTE_ABSTRACT) != 0L)
            mods.append("abstract ")
        else if (!typeDef.isValueType && !typeDef.isEnum && (typeDef.flags and TYPE_ATTRIBUTE_SEALED) != 0L)
            mods.append("sealed ")
        writer.append(mods)
        val kind: TypeKind
        if ((typeDef.flags and TYPE_ATTRIBUTE_INTERFACE) != 0L) {
            writer.append("interface "); kind = TypeKind.INTERFACE
        } else if (typeDef.isEnum) {
            writer.append("enum "); kind = TypeKind.ENUM
        } else if (typeDef.isValueType) {
            writer.append("struct "); kind = TypeKind.STRUCT
        } else {
            writer.append("class "); kind = TypeKind.CLASS
        }
        val typeName = executor.getTypeDefName(typeDef, false, true)
        writer.append(typeName)
        if (extends.size > 0) writer.append(" : ").append(extends.joinToString(", "))
        if (config.dumpTypeDefIndex) writer.append(" // TypeDefIndex: $typeDefIndex\n{") else writer.append("\n{")

        val fieldEntries = if (collectIndex) ArrayList<FieldEntry>() else null
        val propertyEntries = if (collectIndex) ArrayList<PropertyEntry>() else null
        val methodEntries = if (collectIndex) ArrayList<MethodEntry>() else null

        // fields
        if (config.dumpField && typeDef.fieldCount > 0) {
            writer.append("\n\t// Fields\n")
            val fieldEnd = typeDef.fieldStart + typeDef.fieldCount
            for (i in typeDef.fieldStart until fieldEnd) {
                val fieldDef = metadata.fieldDefs[i]
                val fieldType = il2Cpp.types[fieldDef.typeIndex]
                var isStatic = false
                var isConst = false
                var fieldAttributes = ""
                if (config.dumpAttribute) {
                    fieldAttributes = getCustomAttribute(imageDef, fieldDef.customAttributeIndex, fieldDef.token, "\t")
                    writer.append(fieldAttributes)
                }
                writer.append("\t")
                val fmods = StringBuilder()
                when (fieldType.attrs and FIELD_ATTRIBUTE_FIELD_ACCESS_MASK) {
                    FIELD_ATTRIBUTE_PRIVATE -> fmods.append("private ")
                    FIELD_ATTRIBUTE_PUBLIC -> fmods.append("public ")
                    FIELD_ATTRIBUTE_FAMILY -> fmods.append("protected ")
                    FIELD_ATTRIBUTE_ASSEMBLY, FIELD_ATTRIBUTE_FAM_AND_ASSEM -> fmods.append("internal ")
                    FIELD_ATTRIBUTE_FAM_OR_ASSEM -> fmods.append("protected internal ")
                }
                if ((fieldType.attrs and FIELD_ATTRIBUTE_LITERAL) != 0) {
                    isConst = true
                    fmods.append("const ")
                } else {
                    if ((fieldType.attrs and FIELD_ATTRIBUTE_STATIC) != 0) {
                        isStatic = true
                        fmods.append("static ")
                    }
                    if ((fieldType.attrs and FIELD_ATTRIBUTE_INIT_ONLY) != 0) {
                        fmods.append("readonly ")
                    }
                }
                writer.append(fmods)
                val fieldTypeName = executor.getTypeName(fieldType, false, false)
                val fieldName = metadata.getStringFromIndex(fieldDef.nameIndex)
                writer.append(fieldTypeName).append(' ').append(fieldName)
                var defaultText: String? = null
                val fieldDefaultValue = metadata.getFieldDefaultValueFromIndex(i)
                if (fieldDefaultValue != null && fieldDefaultValue.dataIndex != -1) {
                    val (ok, value) = executor.tryGetDefaultValue(fieldDefaultValue.typeIndex, fieldDefaultValue.dataIndex)
                    if (ok) {
                        writer.append(" = ")
                        defaultText = when (value) {
                            is String -> "\"${CSharp.escape(value)}\""
                            is Char -> "'\\x${Integer.toHexString(value.code)}'"
                            null -> "null"
                            else -> CSharp.toString(value)
                        }
                        writer.append(defaultText)
                    } else {
                        val t = " /*Metadata offset 0x${hex(value as Long)}*/"
                        writer.append(t)
                        defaultText = t.trim()
                    }
                }
                var offset = Int.MIN_VALUE
                if (config.dumpFieldOffset && !isConst) {
                    offset = il2Cpp.getFieldOffsetFromIndex(typeDefIndex, i - typeDef.fieldStart, i, typeDef.isValueType, isStatic)
                    writer.append("; // 0x").append(Integer.toHexString(offset).uppercase()).append("\n")
                } else {
                    writer.append(";\n")
                }
                if (fieldEntries != null) {
                    if (offset == Int.MIN_VALUE && !isConst) {
                        offset = il2Cpp.getFieldOffsetFromIndex(typeDefIndex, i - typeDef.fieldStart, i, typeDef.isValueType, isStatic)
                    }
                    fieldEntries.add(FieldEntry(fieldName, fieldTypeName, fmods.toString(), offset, defaultText, isStatic, isConst, fieldAttributes))
                }
            }
        }
        // properties
        if (config.dumpProperty && typeDef.propertyCount > 0) {
            writer.append("\n\t// Properties\n")
            val propertyEnd = typeDef.propertyStart + typeDef.propertyCount
            for (i in typeDef.propertyStart until propertyEnd) {
                val propertyDef = metadata.propertyDefs[i]
                if (config.dumpAttribute) {
                    writer.append(getCustomAttribute(imageDef, propertyDef.customAttributeIndex, propertyDef.token, "\t"))
                }
                writer.append("\t")
                var pmods = ""
                var ptype = ""
                val pname = metadata.getStringFromIndex(propertyDef.nameIndex)
                if (propertyDef.get >= 0) {
                    val methodDef = metadata.methodDefs[typeDef.methodStart + propertyDef.get]
                    pmods = getModifiers(methodDef)
                    writer.append(pmods)
                    val propertyType = il2Cpp.types[methodDef.returnType]
                    ptype = executor.getTypeName(propertyType, false, false)
                    writer.append("$ptype $pname { ")
                } else if (propertyDef.set >= 0) {
                    val methodDef = metadata.methodDefs[typeDef.methodStart + propertyDef.set]
                    pmods = getModifiers(methodDef)
                    writer.append(pmods)
                    val parameterDef = metadata.parameterDefs[methodDef.parameterStart]
                    val propertyType = il2Cpp.types[parameterDef.typeIndex]
                    ptype = executor.getTypeName(propertyType, false, false)
                    writer.append("$ptype $pname { ")
                }
                if (propertyDef.get >= 0) writer.append("get; ")
                if (propertyDef.set >= 0) writer.append("set; ")
                writer.append("}")
                writer.append("\n")
                propertyEntries?.add(PropertyEntry(pname, ptype, pmods, propertyDef.get >= 0, propertyDef.set >= 0))
            }
        }
        // methods
        if (config.dumpMethod && typeDef.methodCount > 0) {
            writer.append("\n\t// Methods\n")
            val methodEnd = typeDef.methodStart + typeDef.methodCount
            for (i in typeDef.methodStart until methodEnd) {
                writer.append("\n")
                val methodDef = metadata.methodDefs[i]
                val isAbstract = (methodDef.flags and METHOD_ATTRIBUTE_ABSTRACT) != 0
                var methodAttributes = ""
                if (config.dumpAttribute) {
                    methodAttributes = getCustomAttribute(imageDef, methodDef.customAttributeIndex, methodDef.token, "\t")
                    writer.append(methodAttributes)
                }
                var rva = -1L
                var fileOffset = -1L
                var va = -1L
                if (config.dumpMethodOffset) {
                    val methodPointer = il2Cpp.getMethodPointer(imageName, methodDef)
                    if (!isAbstract && methodPointer != 0L) {
                        rva = il2Cpp.getRVA(methodPointer)
                        fileOffset = il2Cpp.mapVATR(methodPointer)
                        va = methodPointer
                        writer.append("\t// RVA: 0x${hex(rva)} Offset: 0x${hex(fileOffset)} VA: 0x${hex(methodPointer)}")
                    } else {
                        writer.append("\t// RVA: -1 Offset: -1")
                    }
                    if (methodDef.slot != 0xFFFF) {
                        writer.append(" Slot: ${methodDef.slot}")
                    }
                    writer.append("\n")
                } else if (methodEntries != null) {
                    try {
                        val methodPointer = il2Cpp.getMethodPointer(imageName, methodDef)
                        if (!isAbstract && methodPointer != 0L) {
                            rva = il2Cpp.getRVA(methodPointer)
                            fileOffset = il2Cpp.mapVATR(methodPointer)
                            va = methodPointer
                        }
                    } catch (e: Exception) {
                        // index only
                    }
                }
                writer.append("\t")
                val mmods = getModifiers(methodDef)
                writer.append(mmods)
                val methodReturnType = il2Cpp.types[methodDef.returnType]
                var methodName = metadata.getStringFromIndex(methodDef.nameIndex)
                if (methodDef.genericContainerIndex >= 0) {
                    val genericContainer = metadata.genericContainers[methodDef.genericContainerIndex]
                    methodName += executor.getGenericContainerParams(genericContainer)
                }
                var retPrefix = ""
                if (methodReturnType.byref == 1) {
                    writer.append("ref ")
                    retPrefix = "ref "
                }
                val returnTypeName = executor.getTypeName(methodReturnType, false, false)
                writer.append("$returnTypeName $methodName(")
                val parameterStrs = ArrayList<String>()
                val params = if (methodEntries != null) ArrayList<ParamEntry>() else null
                for (j in 0 until methodDef.parameterCount) {
                    var parameterStr = ""
                    val parameterDef = metadata.parameterDefs[methodDef.parameterStart + j]
                    val parameterName = metadata.getStringFromIndex(parameterDef.nameIndex)
                    val parameterType = il2Cpp.types[parameterDef.typeIndex]
                    val parameterTypeName = executor.getTypeName(parameterType, false, false)
                    if (parameterType.byref == 1) {
                        parameterStr += if ((parameterType.attrs and PARAM_ATTRIBUTE_OUT) != 0 && (parameterType.attrs and PARAM_ATTRIBUTE_IN) == 0) {
                            "out "
                        } else if ((parameterType.attrs and PARAM_ATTRIBUTE_OUT) == 0 && (parameterType.attrs and PARAM_ATTRIBUTE_IN) != 0) {
                            "in "
                        } else {
                            "ref "
                        }
                    } else {
                        if ((parameterType.attrs and PARAM_ATTRIBUTE_IN) != 0) parameterStr += "[In] "
                        if ((parameterType.attrs and PARAM_ATTRIBUTE_OUT) != 0) parameterStr += "[Out] "
                    }
                    val prefix = parameterStr
                    parameterStr += "$parameterTypeName $parameterName"
                    val parameterDefault = metadata.getParameterDefaultValueFromIndex(methodDef.parameterStart + j)
                    if (parameterDefault != null && parameterDefault.dataIndex != -1) {
                        val (ok, value) = executor.tryGetDefaultValue(parameterDefault.typeIndex, parameterDefault.dataIndex)
                        if (ok) {
                            parameterStr += " = "
                            when (value) {
                                is String -> parameterStr += "\"${CSharp.escape(value)}\""
                                is Char -> parameterStr += "'\\x${Integer.toHexString(value.code)}'"
                                null -> writer.append("null") // replicates the original behaviour
                                else -> parameterStr += CSharp.toString(value)
                            }
                        } else {
                            parameterStr += " /*Metadata offset 0x${hex(value as Long)}*/"
                        }
                    }
                    parameterStrs.add(parameterStr)
                    params?.add(ParamEntry(parameterName, parameterTypeName, prefix))
                }
                writer.append(parameterStrs.joinToString(", "))
                if (isAbstract) writer.append(");\n") else writer.append(") { }\n")

                val genericInstances = ArrayList<GenericInstanceEntry>()
                val methodSpecs = il2Cpp.methodDefinitionMethodSpecs[i]
                if (methodSpecs != null) {
                    writer.append("\t/* GenericInstMethod :\n")
                    val groups = LinkedHashMap<Long, MutableList<dev.il2cpp.dumper.core.il2cpp.Il2CppMethodSpec>>()
                    for (ms in methodSpecs) {
                        groups.getOrPut(il2Cpp.methodSpecGenericMethodPointers[ms]!!) { ArrayList() }.add(ms)
                    }
                    for ((genericMethodPointer, group) in groups) {
                        writer.append("\t|\n")
                        var gRva = -1L
                        var gOff = -1L
                        if (genericMethodPointer != 0L) {
                            gRva = il2Cpp.getRVA(genericMethodPointer)
                            gOff = il2Cpp.mapVATR(genericMethodPointer)
                            writer.append("\t|-RVA: 0x${hex(gRva)} Offset: 0x${hex(gOff)} VA: 0x${hex(genericMethodPointer)}\n")
                        } else {
                            writer.append("\t|-RVA: -1 Offset: -1\n")
                        }
                        val names = ArrayList<String>()
                        for (methodSpec in group) {
                            val (methodSpecTypeName, methodSpecMethodName) = executor.getMethodSpecName(methodSpec)
                            writer.append("\t|-$methodSpecTypeName.$methodSpecMethodName\n")
                            names.add("$methodSpecTypeName.$methodSpecMethodName")
                        }
                        if (methodEntries != null) {
                            genericInstances.add(GenericInstanceEntry(gRva, gOff, if (genericMethodPointer != 0L) genericMethodPointer else -1L, names))
                        }
                    }
                    writer.append("\t*/\n")
                }
                methodEntries?.add(
                    MethodEntry(
                        methodName, retPrefix + returnTypeName, params!!, mmods, rva, fileOffset, va,
                        if (methodDef.slot != 0xFFFF) methodDef.slot else -1,
                        (methodDef.flags and METHOD_ATTRIBUTE_STATIC) != 0, isAbstract, methodAttributes,
                        genericInstances, methodDef.token
                    )
                )
            }
        }
        writer.append("}\n")
        if (collectIndex) {
            typeEntries.add(
                TypeEntry(
                    typeDefIndex, imageIndex, namespace, typeName, kind, mods.toString(), extends.joinToString(", "),
                    typeAttributes, fieldEntries!!, propertyEntries!!, methodEntries!!
                )
            )
        }
    }

    fun getCustomAttribute(imageDef: Il2CppImageDefinition, customAttributeIndex: Int, token: Long, padding: String = ""): String {
        if (il2Cpp.version < 21) return ""
        val attributeIndex = metadata.getCustomAttributeIndex(imageDef, customAttributeIndex, token)
        if (attributeIndex >= 0) {
            if (il2Cpp.version < 29) {
                val methodPointer = executor.customAttributeGenerators!![attributeIndex]
                val fixedMethodPointer = il2Cpp.getRVA(methodPointer)
                val attributeTypeRange = metadata.attributeTypeRanges[attributeIndex]
                val sb = StringBuilder()
                for (i in 0 until attributeTypeRange.count) {
                    val typeIndex = metadata.attributeTypes[attributeTypeRange.start + i]
                    sb.append(padding).append('[').append(executor.getTypeName(il2Cpp.types[typeIndex], false, false))
                        .append("] // RVA: 0x").append(hex(fixedMethodPointer))
                        .append(" Offset: 0x").append(hex(il2Cpp.mapVATR(methodPointer)))
                        .append(" VA: 0x").append(hex(methodPointer)).append('\n')
                }
                return sb.toString()
            } else {
                val startRange = metadata.attributeDataRanges[attributeIndex]
                val endRange = metadata.attributeDataRanges[attributeIndex + 1]
                metadata.position = metadata.header.attributeDataOffset + startRange.startOffset
                val buff = metadata.readBytes((endRange.startOffset - startRange.startOffset).toInt())
                val reader = CustomAttributeDataReader(executor, buff)
                if (reader.count == 0L) return ""
                val sb = StringBuilder()
                for (i in 0 until reader.count) {
                    sb.append(padding)
                    sb.append(reader.getStringCustomAttributeData())
                    sb.append('\n')
                }
                return sb.toString()
            }
        }
        return ""
    }

    fun getModifiers(methodDef: Il2CppMethodDefinition): String {
        methodModifiers[methodDef]?.let { return it }
        val str = StringBuilder()
        when (methodDef.flags and METHOD_ATTRIBUTE_MEMBER_ACCESS_MASK) {
            METHOD_ATTRIBUTE_PRIVATE -> str.append("private ")
            METHOD_ATTRIBUTE_PUBLIC -> str.append("public ")
            METHOD_ATTRIBUTE_FAMILY -> str.append("protected ")
            METHOD_ATTRIBUTE_ASSEM, METHOD_ATTRIBUTE_FAM_AND_ASSEM -> str.append("internal ")
            METHOD_ATTRIBUTE_FAM_OR_ASSEM -> str.append("protected internal ")
        }
        if ((methodDef.flags and METHOD_ATTRIBUTE_STATIC) != 0) str.append("static ")
        if ((methodDef.flags and METHOD_ATTRIBUTE_ABSTRACT) != 0) {
            str.append("abstract ")
            if ((methodDef.flags and METHOD_ATTRIBUTE_VTABLE_LAYOUT_MASK) == METHOD_ATTRIBUTE_REUSE_SLOT) str.append("override ")
        } else if ((methodDef.flags and METHOD_ATTRIBUTE_FINAL) != 0) {
            if ((methodDef.flags and METHOD_ATTRIBUTE_VTABLE_LAYOUT_MASK) == METHOD_ATTRIBUTE_REUSE_SLOT) str.append("sealed override ")
        } else if ((methodDef.flags and METHOD_ATTRIBUTE_VIRTUAL) != 0) {
            if ((methodDef.flags and METHOD_ATTRIBUTE_VTABLE_LAYOUT_MASK) == METHOD_ATTRIBUTE_NEW_SLOT) str.append("virtual ") else str.append("override ")
        }
        if ((methodDef.flags and METHOD_ATTRIBUTE_PINVOKE_IMPL) != 0) str.append("extern ")
        val result = str.toString()
        methodModifiers[methodDef] = result
        return result
    }
}
