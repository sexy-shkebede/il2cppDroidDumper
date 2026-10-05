package dev.il2cpp.dumper.core.utils

import dev.il2cpp.dumper.core.il2cpp.Il2Cpp
import dev.il2cpp.dumper.core.il2cpp.Il2CppGenericClass
import dev.il2cpp.dumper.core.il2cpp.Il2CppGenericContainer
import dev.il2cpp.dumper.core.il2cpp.Il2CppGenericContext
import dev.il2cpp.dumper.core.il2cpp.Il2CppGenericInst
import dev.il2cpp.dumper.core.il2cpp.Il2CppGenericParameter
import dev.il2cpp.dumper.core.il2cpp.Il2CppMethodDefinition
import dev.il2cpp.dumper.core.il2cpp.Il2CppMethodSpec
import dev.il2cpp.dumper.core.il2cpp.Il2CppRGCTXDefinition
import dev.il2cpp.dumper.core.il2cpp.Il2CppType
import dev.il2cpp.dumper.core.il2cpp.Il2CppTypeDefinition
import dev.il2cpp.dumper.core.il2cpp.Il2CppTypeEnum
import dev.il2cpp.dumper.core.il2cpp.Metadata
import dev.il2cpp.dumper.core.io.DataReader
import dev.il2cpp.dumper.core.io.Utf8

/** Port of Il2CppExecutor.cs */
class Il2CppExecutor(val metadata: Metadata, val il2Cpp: Il2Cpp) {
    var customAttributeGenerators: LongArray? = null

    init {
        if (il2Cpp.version >= 27 && il2Cpp.version < 29) {
            val total = metadata.imageDefs.sumOf { it.customAttributeCount }
            val gens = LongArray(total.toInt())
            for (imageDef in metadata.imageDefs) {
                val imageDefName = metadata.getStringFromIndex(imageDef.nameIndex)
                val codeGenModule = il2Cpp.codeGenModules[imageDefName]
                    ?: throw NoSuchElementException("The given key '$imageDefName' was not present in the dictionary.")
                if (imageDef.customAttributeCount > 0) {
                    val pointers = il2Cpp.readUIntPtrArray(il2Cpp.mapVATR(codeGenModule.customAttributeCacheGenerator), imageDef.customAttributeCount)
                    System.arraycopy(pointers, 0, gens, imageDef.customAttributeStart, pointers.size)
                }
            }
            customAttributeGenerators = gens
        } else if (il2Cpp.version < 27) {
            customAttributeGenerators = il2Cpp.customAttributeGenerators
        }
    }

    fun getTypeName(il2CppType: Il2CppType, addNamespace: Boolean, isNested: Boolean): String {
        when (il2CppType.type) {
            Il2CppTypeEnum.ARRAY -> {
                val arrayType = il2Cpp.mapArrayType(il2CppType.array)
                val elementType = il2Cpp.getIl2CppType(arrayType.etype)!!
                return "${getTypeName(elementType, addNamespace, false)}[${",".repeat(arrayType.rank - 1)}]"
            }
            Il2CppTypeEnum.SZARRAY -> {
                val elementType = il2Cpp.getIl2CppType(il2CppType.dataType)!!
                return "${getTypeName(elementType, addNamespace, false)}[]"
            }
            Il2CppTypeEnum.PTR -> {
                val oriType = il2Cpp.getIl2CppType(il2CppType.dataType)!!
                return "${getTypeName(oriType, addNamespace, false)}*"
            }
            Il2CppTypeEnum.VAR, Il2CppTypeEnum.MVAR -> {
                val param = getGenericParameteFromIl2CppType(il2CppType)
                return metadata.getStringFromIndex(param.nameIndex)
            }
            Il2CppTypeEnum.CLASS, Il2CppTypeEnum.VALUETYPE, Il2CppTypeEnum.GENERICINST -> {
                val str = StringBuilder()
                val typeDef: Il2CppTypeDefinition
                var genericClass: Il2CppGenericClass? = null
                if (il2CppType.type == Il2CppTypeEnum.GENERICINST) {
                    genericClass = il2Cpp.mapGenericClass(il2CppType.genericClass)
                    typeDef = getGenericClassTypeDefinition(genericClass)!!
                } else {
                    typeDef = getTypeDefinitionFromIl2CppType(il2CppType)
                }
                if (typeDef.declaringTypeIndex != -1) {
                    str.append(getTypeName(il2Cpp.types[typeDef.declaringTypeIndex], addNamespace, true))
                    str.append('.')
                } else if (addNamespace) {
                    val namespace = metadata.getStringFromIndex(typeDef.namespaceIndex)
                    if (namespace != "") {
                        str.append(namespace).append('.')
                    }
                }
                val typeName = metadata.getStringFromIndex(typeDef.nameIndex)
                val index = typeName.indexOf('`')
                if (index != -1) str.append(typeName, 0, index) else str.append(typeName)

                if (isNested) return str.toString()

                if (genericClass != null) {
                    val genericInst = il2Cpp.mapGenericInst(genericClass.context.classInst)
                    str.append(getGenericInstParams(genericInst))
                } else if (typeDef.genericContainerIndex >= 0) {
                    val genericContainer = metadata.genericContainers[typeDef.genericContainerIndex]
                    str.append(getGenericContainerParams(genericContainer))
                }
                return str.toString()
            }
            else -> return TypeString[il2CppType.type]
                ?: throw NoSuchElementException("The given key '${il2CppType.type}' was not present in the dictionary.")
        }
    }

    fun getTypeDefName(typeDef: Il2CppTypeDefinition, addNamespace: Boolean, genericParameter: Boolean): String {
        var prefix = ""
        if (typeDef.declaringTypeIndex != -1) {
            prefix = getTypeName(il2Cpp.types[typeDef.declaringTypeIndex], addNamespace, true) + "."
        } else if (addNamespace) {
            val namespace = metadata.getStringFromIndex(typeDef.namespaceIndex)
            if (namespace != "") prefix = "$namespace."
        }
        var typeName = metadata.getStringFromIndex(typeDef.nameIndex)
        if (typeDef.genericContainerIndex >= 0) {
            val index = typeName.indexOf('`')
            if (index != -1) typeName = typeName.substring(0, index)
            if (genericParameter) {
                val genericContainer = metadata.genericContainers[typeDef.genericContainerIndex]
                typeName += getGenericContainerParams(genericContainer)
            }
        }
        return prefix + typeName
    }

    fun getGenericInstParams(genericInst: Il2CppGenericInst): String {
        val names = ArrayList<String>()
        val pointers = il2Cpp.mapVATRPointers(genericInst.typeArgv, genericInst.typeArgc)
        for (i in 0 until genericInst.typeArgc.toInt()) {
            val il2CppType = il2Cpp.getIl2CppType(pointers[i])!!
            names.add(getTypeName(il2CppType, false, false))
        }
        return "<${names.joinToString(", ")}>"
    }

    fun getGenericContainerParams(genericContainer: Il2CppGenericContainer): String {
        val names = ArrayList<String>()
        for (i in 0 until genericContainer.typeArgc) {
            val genericParameterIndex = genericContainer.genericParameterStart + i
            val genericParameter = metadata.genericParameters[genericParameterIndex]
            names.add(metadata.getStringFromIndex(genericParameter.nameIndex))
        }
        return "<${names.joinToString(", ")}>"
    }

    fun getMethodSpecName(methodSpec: Il2CppMethodSpec, addNamespace: Boolean = false): Pair<String, String> {
        val methodDef = metadata.methodDefs[methodSpec.methodDefinitionIndex]
        val typeDef = metadata.typeDefs[methodDef.declaringType]
        var typeName = getTypeDefName(typeDef, addNamespace, false)
        if (methodSpec.classIndexIndex != -1) {
            val classInst = il2Cpp.genericInsts[methodSpec.classIndexIndex]
            typeName += getGenericInstParams(classInst)
        }
        var methodName = metadata.getStringFromIndex(methodDef.nameIndex)
        if (methodSpec.methodIndexIndex != -1) {
            val methodInst = il2Cpp.genericInsts[methodSpec.methodIndexIndex]
            methodName += getGenericInstParams(methodInst)
        }
        return typeName to methodName
    }

    fun getMethodSpecGenericContext(methodSpec: Il2CppMethodSpec): Il2CppGenericContext {
        var classInstPointer = 0L
        var methodInstPointer = 0L
        if (methodSpec.classIndexIndex != -1) classInstPointer = il2Cpp.genericInstPointers[methodSpec.classIndexIndex]
        if (methodSpec.methodIndexIndex != -1) methodInstPointer = il2Cpp.genericInstPointers[methodSpec.methodIndexIndex]
        return Il2CppGenericContext(classInstPointer, methodInstPointer)
    }

    fun getRGCTXDefinition(imageName: String, typeDef: Il2CppTypeDefinition): List<Il2CppRGCTXDefinition>? {
        if (il2Cpp.version >= 24.2) {
            return il2Cpp.rgctxsDictionary[imageName]!![typeDef.token]
        }
        if (typeDef.rgctxCount > 0) {
            return metadata.rgctxEntries.subList(typeDef.rgctxStartIndex, typeDef.rgctxStartIndex + typeDef.rgctxCount)
        }
        return null
    }

    fun getRGCTXDefinition(imageName: String, methodDef: Il2CppMethodDefinition): List<Il2CppRGCTXDefinition>? {
        if (il2Cpp.version >= 24.2) {
            return il2Cpp.rgctxsDictionary[imageName]!![methodDef.token]
        }
        if (methodDef.rgctxCount > 0) {
            return metadata.rgctxEntries.subList(methodDef.rgctxStartIndex, methodDef.rgctxStartIndex + methodDef.rgctxCount)
        }
        return null
    }

    fun getGenericClassTypeDefinition(genericClass: Il2CppGenericClass): Il2CppTypeDefinition? {
        if (il2Cpp.version >= 27) {
            val il2CppType = il2Cpp.getIl2CppType(genericClass.type) ?: return null
            return getTypeDefinitionFromIl2CppType(il2CppType)
        }
        if (genericClass.typeDefinitionIndex == 4294967295L || genericClass.typeDefinitionIndex == -1L) {
            return null
        }
        return metadata.typeDefs[genericClass.typeDefinitionIndex.toInt()]
    }

    fun getTypeDefinitionFromIl2CppType(il2CppType: Il2CppType): Il2CppTypeDefinition {
        if (il2Cpp.version >= 27 && il2Cpp.isDumped) {
            val offset = il2CppType.typeHandle - metadata.imageBase - metadata.header.typeDefinitionsOffset
            val index = java.lang.Long.divideUnsigned(offset, metadata.sizeOfTypeDefinition().toLong())
            return metadata.typeDefs[index.toInt()]
        }
        return metadata.typeDefs[checkIndex(il2CppType.klassIndex)]
    }

    fun getGenericParameteFromIl2CppType(il2CppType: Il2CppType): Il2CppGenericParameter {
        if (il2Cpp.version >= 27 && il2Cpp.isDumped) {
            val offset = il2CppType.genericParameterHandle - metadata.imageBase - metadata.header.genericParametersOffset
            val index = java.lang.Long.divideUnsigned(offset, metadata.sizeOfGenericParameter().toLong())
            return metadata.genericParameters[index.toInt()]
        }
        return metadata.genericParameters[checkIndex(il2CppType.genericParameterIndex)]
    }

    private fun checkIndex(v: Long): Int {
        if (v < 0 || v > Int.MAX_VALUE) throw IndexOutOfBoundsException("Index was outside the bounds of the array.")
        return v.toInt()
    }

    fun getSectionHelper(): SectionHelper =
        il2Cpp.getSectionHelper(metadata.methodDefs.count { it.methodIndex >= 0 }, metadata.typeDefs.size, metadata.imageDefs.size)

    /** Returns (true, value) on success or (false, pointer). */
    fun tryGetDefaultValue(typeIndex: Int, dataIndex: Int): Pair<Boolean, Any?> {
        val pointer = metadata.getDefaultValueFromIndex(dataIndex)
        val defaultValueType = il2Cpp.types[typeIndex]
        metadata.position = pointer
        val blob = getConstantValueFromBlob(defaultValueType.type, metadata)
        return if (blob != null) true to blob.value else false to pointer
    }

    fun getConstantValueFromBlob(type: Int, reader: DataReader): BlobValue? {
        val value = BlobValue(null, type)
        when (type) {
            Il2CppTypeEnum.BOOLEAN -> value.value = reader.readBoolean()
            Il2CppTypeEnum.U1 -> value.value = reader.readByte()
            Il2CppTypeEnum.I1 -> value.value = reader.readSByte()
            Il2CppTypeEnum.CHAR -> {
                val b = reader.readBytes(2)
                if (b.size < 2) throw IndexOutOfBoundsException("char")
                value.value = ((b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8)).toChar()
            }
            Il2CppTypeEnum.U2 -> value.value = reader.readUInt16()
            Il2CppTypeEnum.I2 -> value.value = reader.readInt16()
            Il2CppTypeEnum.U4 -> value.value = if (il2Cpp.version >= 29) reader.readCompressedUInt32() else reader.readUInt32()
            Il2CppTypeEnum.I4 -> value.value = if (il2Cpp.version >= 29) reader.readCompressedInt32() else reader.readInt32()
            Il2CppTypeEnum.U8 -> value.value = reader.readUInt64().toULong()
            Il2CppTypeEnum.I8 -> value.value = reader.readInt64()
            Il2CppTypeEnum.R4 -> value.value = reader.readSingle()
            Il2CppTypeEnum.R8 -> value.value = reader.readDouble()
            Il2CppTypeEnum.STRING -> {
                if (il2Cpp.version >= 29) {
                    val length = reader.readCompressedInt32()
                    value.value = if (length == -1) null else Utf8.decode(reader.readBytes(length))
                } else {
                    val length = reader.readInt32()
                    value.value = reader.readStringChars(length)
                }
            }
            Il2CppTypeEnum.SZARRAY -> {
                val arrayLen = reader.readCompressedInt32()
                if (arrayLen == -1) {
                    value.value = null
                } else {
                    val array = arrayOfNulls<BlobValue>(arrayLen)
                    val (arrayElementType, firstEnum) = readEncodedTypeEnum(reader)
                    var enumType = firstEnum
                    val arrayElementsAreDifferent = reader.readByte()
                    for (i in 0 until arrayLen) {
                        var elementType = arrayElementType
                        if (arrayElementsAreDifferent == 1) {
                            val r = readEncodedTypeEnum(reader)
                            elementType = r.first
                            enumType = r.second
                        }
                        val data = getConstantValueFromBlob(elementType, reader)
                            ?: throw NullPointerException("Unsupported blob element type $elementType")
                        data.il2CppTypeEnum = elementType
                        data.enumType = enumType
                        array[i] = data
                    }
                    value.value = array
                }
            }
            Il2CppTypeEnum.IL2CPP_TYPE_INDEX -> {
                val typeIndex = reader.readCompressedInt32()
                value.value = if (typeIndex == -1) null else il2Cpp.types[typeIndex]
            }
            else -> return null
        }
        return value
    }

    fun readEncodedTypeEnum(reader: DataReader): Pair<Int, Il2CppType?> {
        var enumType: Il2CppType? = null
        var type = reader.readByte()
        if (type == Il2CppTypeEnum.ENUM) {
            val enumTypeIndex = reader.readCompressedInt32()
            enumType = il2Cpp.types[enumTypeIndex]
            val typeDef = getTypeDefinitionFromIl2CppType(enumType)
            type = il2Cpp.types[typeDef.elementTypeIndex].type
        }
        return type to enumType
    }

    companion object {
        val TypeString: Map<Int, String> = mapOf(
            1 to "void", 2 to "bool", 3 to "char", 4 to "sbyte", 5 to "byte", 6 to "short", 7 to "ushort",
            8 to "int", 9 to "uint", 10 to "long", 11 to "ulong", 12 to "float", 13 to "double", 14 to "string",
            22 to "TypedReference", 24 to "IntPtr", 25 to "UIntPtr", 28 to "object",
        )
    }
}
