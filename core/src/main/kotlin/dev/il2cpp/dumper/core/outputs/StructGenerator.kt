package dev.il2cpp.dumper.core.outputs

import dev.il2cpp.dumper.core.DumpLogger
import dev.il2cpp.dumper.core.DumpProgress
import dev.il2cpp.dumper.core.formats.PE
import dev.il2cpp.dumper.core.il2cpp.Il2Cpp
import dev.il2cpp.dumper.core.il2cpp.Il2CppGenericContext
import dev.il2cpp.dumper.core.il2cpp.Il2CppMethodDefinition
import dev.il2cpp.dumper.core.il2cpp.Il2CppMetadataUsage
import dev.il2cpp.dumper.core.il2cpp.Il2CppRGCTXDataType
import dev.il2cpp.dumper.core.il2cpp.Il2CppType
import dev.il2cpp.dumper.core.il2cpp.Il2CppTypeDefinition
import dev.il2cpp.dumper.core.il2cpp.Il2CppTypeEnum
import dev.il2cpp.dumper.core.il2cpp.Metadata
import dev.il2cpp.dumper.core.io.Utf8Writer
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.FIELD_ATTRIBUTE_LITERAL
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.FIELD_ATTRIBUTE_STATIC
import dev.il2cpp.dumper.core.outputs.Il2CppConstants.METHOD_ATTRIBUTE_STATIC
import dev.il2cpp.dumper.core.utils.CSharp
import dev.il2cpp.dumper.core.utils.Il2CppExecutor
import dev.il2cpp.dumper.core.utils.hex
import dev.il2cpp.dumper.core.utils.umin
import dev.il2cpp.dumper.core.utils.ult
import java.io.File
import java.io.FileOutputStream
import java.util.IdentityHashMap

class ScriptMethod(var address: Long = 0, var name: String? = null, var signature: String? = null, var typeSignature: String? = null)
class ScriptString(var address: Long = 0, var value: String? = null)
class ScriptMetadata(var address: Long = 0, var name: String? = null, var signature: String? = null)
class ScriptMetadataMethod(var address: Long = 0, var name: String? = null, var methodAddress: Long = 0)

class ScriptJson {
    val scriptMethod = ArrayList<ScriptMethod>()
    val scriptString = ArrayList<ScriptString>()
    val scriptMetadata = ArrayList<ScriptMetadata>()
    val scriptMetadataMethod = ArrayList<ScriptMetadataMethod>()
    var addresses: LongArray = LongArray(0)

    fun write(w: JsonWriter) {
        w.beginObject()
        w.name("ScriptMethod").beginArray()
        for (m in scriptMethod) {
            w.beginObject()
            w.name("Address").valueULong(m.address)
            w.name("Name").value(m.name)
            w.name("Signature").value(m.signature)
            w.name("TypeSignature").value(m.typeSignature)
            w.endObject()
        }
        w.endArray()
        w.name("ScriptString").beginArray()
        for (s in scriptString) {
            w.beginObject()
            w.name("Address").valueULong(s.address)
            w.name("Value").value(s.value)
            w.endObject()
        }
        w.endArray()
        w.name("ScriptMetadata").beginArray()
        for (m in scriptMetadata) {
            w.beginObject()
            w.name("Address").valueULong(m.address)
            w.name("Name").value(m.name)
            w.name("Signature").value(m.signature)
            w.endObject()
        }
        w.endArray()
        w.name("ScriptMetadataMethod").beginArray()
        for (m in scriptMetadataMethod) {
            w.beginObject()
            w.name("Address").valueULong(m.address)
            w.name("Name").value(m.name)
            w.name("MethodAddress").valueULong(m.methodAddress)
            w.endObject()
        }
        w.endArray()
        w.name("Addresses").beginArray()
        for (a in addresses) w.valueULong(a)
        w.endArray()
        w.endObject()
    }
}

class StructFieldInfo(var fieldTypeName: String = "", var fieldName: String = "", var isValueType: Boolean = false, var isCustomType: Boolean = false)
class StructVTableMethodInfo(var methodName: String = "")
class StructRGCTXInfo(var type: Int = 0, var typeName: String? = null, var className: String? = null, var methodName: String? = null)

class StructInfo {
    var typeName: String = ""
    var isValueType = false
    var parent: String? = null
    val fields = ArrayList<StructFieldInfo>()
    val staticFields = ArrayList<StructFieldInfo>()
    var vTableMethod: Array<StructVTableMethodInfo?> = emptyArray()
    val rgctxs = ArrayList<StructRGCTXInfo>()
}

/** Port of StructGenerator.cs: writes script.json, stringliteral.json and il2cpp.h */
class StructGenerator(private val executor: Il2CppExecutor, private val logger: DumpLogger = DumpLogger.NONE) {
    private val metadata: Metadata = executor.metadata
    private val il2Cpp: Il2Cpp = executor.il2Cpp
    private val typeDefImageNames = IdentityHashMap<Il2CppTypeDefinition, String>()
    private val structNameHashSet = HashSet<String>()
    private val structInfoList = ArrayList<StructInfo>()
    private val structInfoWithStructName = HashMap<String, StructInfo>()
    private val structCache = java.util.Collections.newSetFromMap(IdentityHashMap<StructInfo, Boolean>())
    private val structNameDic = IdentityHashMap<Il2CppTypeDefinition, String>()
    private val genericClassStructNameDic = HashMap<Long, String>()
    private val nameGenericClassDic = HashMap<String, Il2CppType>()
    private val genericClassList = ArrayList<Long>()
    private val arrayClassHeader = StringBuilder()
    private val methodInfoHeader = StringBuilder()
    private val methodInfoCache = HashSet<Long>()

    /** Exposed for the in-app tools (string literal search). */
    var lastScriptJson: ScriptJson? = null
        private set

    private fun structName(typeDef: Il2CppTypeDefinition): String =
        structNameDic[typeDef] ?: throw NoSuchElementException("The given key was not present in the dictionary.")

    fun writeScript(outputDir: File, progress: DumpProgress = DumpProgress.NONE) {
        val json = ScriptJson()
        // unique names
        for (imageDef in metadata.imageDefs) {
            val imageName = metadata.getStringFromIndex(imageDef.nameIndex)
            val typeEnd = imageDef.typeStart + imageDef.typeCount
            var typeIndex = imageDef.typeStart
            while (typeIndex < typeEnd) {
                val typeDef = metadata.typeDefs[typeIndex]
                typeDefImageNames[typeDef] = imageName
                createStructNameDic(typeDef)
                typeIndex++
            }
        }
        // generic instance helpers
        for (il2CppType in il2Cpp.types) {
            if (il2CppType.type != Il2CppTypeEnum.GENERICINST) continue
            val genericClass = il2Cpp.mapGenericClass(il2CppType.genericClass)
            val typeDef = executor.getGenericClassTypeDefinition(genericClass) ?: continue
            val typeBaseName = structName(typeDef)
            val typeToReplaceName = fixName(executor.getTypeDefName(typeDef, true, true))
            val typeReplaceName = fixName(executor.getTypeName(il2CppType, true, false))
            val typeStructName = CSharp.replace(typeBaseName, typeToReplaceName, typeReplaceName)
            nameGenericClassDic[typeStructName] = il2CppType
            genericClassStructNameDic[il2CppType.genericClass] = typeStructName
        }
        // methods
        val total = metadata.typeDefs.size.coerceAtLeast(1)
        var done = 0
        for (imageDef in metadata.imageDefs) {
            val imageName = metadata.getStringFromIndex(imageDef.nameIndex)
            val typeEnd = imageDef.typeStart + imageDef.typeCount
            var typeIndex = imageDef.typeStart
            while (typeIndex < typeEnd) {
                if (done and 0xFF == 0) {
                    progress.checkCancelled()
                    progress.onProgress("il2cpp.h / script.json", done.toFloat() / total)
                }
                done++
                val typeDef = metadata.typeDefs[typeIndex]
                addStruct(typeDef)
                val typeName = executor.getTypeDefName(typeDef, true, true)
                val methodEnd = typeDef.methodStart + typeDef.methodCount
                for (i in typeDef.methodStart until methodEnd) {
                    val methodDef = metadata.methodDefs[i]
                    val methodName = metadata.getStringFromIndex(methodDef.nameIndex)
                    val methodPointer = il2Cpp.getMethodPointer(imageName, methodDef)
                    if (methodPointer != 0L) {
                        val methodTypeSignature = ArrayList<Int>()
                        val scriptMethod = ScriptMethod()
                        json.scriptMethod.add(scriptMethod)
                        scriptMethod.address = il2Cpp.getRVA(methodPointer)
                        val methodFullName = typeName + "\$\$" + methodName
                        scriptMethod.name = methodFullName

                        val methodReturnType = il2Cpp.types[methodDef.returnType]
                        var returnType = parseType(methodReturnType)
                        if (methodReturnType.byref == 1) returnType += "*"
                        methodTypeSignature.add(if (methodReturnType.byref == 1) Il2CppTypeEnum.PTR else methodReturnType.type)
                        var signature = "$returnType ${fixName(methodFullName)} ("
                        val parameterStrs = ArrayList<String>()
                        if ((methodDef.flags and METHOD_ATTRIBUTE_STATIC) == 0) {
                            val thisType = parseType(il2Cpp.types[typeDef.byvalTypeIndex])
                            methodTypeSignature.add(il2Cpp.types[typeDef.byvalTypeIndex].type)
                            parameterStrs.add("$thisType __this")
                        } else if (il2Cpp.version <= 24) {
                            methodTypeSignature.add(Il2CppTypeEnum.PTR)
                            parameterStrs.add("Il2CppObject* __this")
                        }
                        for (j in 0 until methodDef.parameterCount) {
                            val parameterDef = metadata.parameterDefs[methodDef.parameterStart + j]
                            val parameterName = metadata.getStringFromIndex(parameterDef.nameIndex)
                            val parameterType = il2Cpp.types[parameterDef.typeIndex]
                            var parameterCType = parseType(parameterType)
                            if (parameterType.byref == 1) parameterCType += "*"
                            methodTypeSignature.add(if (parameterType.byref == 1) Il2CppTypeEnum.PTR else parameterType.type)
                            parameterStrs.add("$parameterCType ${fixName(parameterName)}")
                        }
                        methodTypeSignature.add(Il2CppTypeEnum.PTR)
                        parameterStrs.add("const MethodInfo* method")
                        signature += parameterStrs.joinToString(", ")
                        signature += ");"
                        scriptMethod.signature = signature
                        scriptMethod.typeSignature = getMethodTypeSignature(methodTypeSignature)
                    }
                    // generic instance methods
                    val methodSpecs = il2Cpp.methodDefinitionMethodSpecs[i]
                    if (methodSpecs != null) {
                        for (methodSpec in methodSpecs) {
                            val genericMethodPointer = il2Cpp.methodSpecGenericMethodPointers[methodSpec]!!
                            if (genericMethodPointer != 0L) {
                                val methodTypeSignature = ArrayList<Int>()
                                val scriptMethod = ScriptMethod()
                                json.scriptMethod.add(scriptMethod)
                                scriptMethod.address = il2Cpp.getRVA(genericMethodPointer)
                                val methodInfoName = "MethodInfo_${hex(scriptMethod.address)}"
                                val structTypeName = structName(typeDef)
                                val rgctxs = generateRGCTX(imageName, methodDef)
                                if (methodInfoCache.add(genericMethodPointer)) {
                                    generateMethodInfo(methodInfoName, structTypeName, rgctxs)
                                }
                                val (methodSpecTypeName, methodSpecMethodName) = executor.getMethodSpecName(methodSpec, true)
                                val methodFullName = methodSpecTypeName + "\$\$" + methodSpecMethodName
                                scriptMethod.name = methodFullName

                                val genericContext = executor.getMethodSpecGenericContext(methodSpec)
                                val methodReturnType = il2Cpp.types[methodDef.returnType]
                                var returnType = parseType(methodReturnType, genericContext)
                                if (methodReturnType.byref == 1) returnType += "*"
                                methodTypeSignature.add(if (methodReturnType.byref == 1) Il2CppTypeEnum.PTR else methodReturnType.type)
                                var signature = "$returnType ${fixName(methodFullName)} ("
                                val parameterStrs = ArrayList<String>()
                                if ((methodDef.flags and METHOD_ATTRIBUTE_STATIC) == 0) {
                                    val thisType: String
                                    if (methodSpec.classIndexIndex != -1) {
                                        val typeBaseName = structName(typeDef)
                                        val typeToReplaceName = fixName(typeName)
                                        val typeReplaceName = fixName(methodSpecTypeName)
                                        val typeStructName = CSharp.replace(typeBaseName, typeToReplaceName, typeReplaceName)
                                        val il2CppType = nameGenericClassDic[typeStructName]
                                        if (il2CppType != null) {
                                            thisType = parseType(il2CppType)
                                            methodTypeSignature.add(il2CppType.type)
                                        } else {
                                            thisType = parseType(il2Cpp.types[typeDef.byvalTypeIndex])
                                            methodTypeSignature.add(il2Cpp.types[typeDef.byvalTypeIndex].type)
                                        }
                                    } else {
                                        thisType = parseType(il2Cpp.types[typeDef.byvalTypeIndex])
                                        methodTypeSignature.add(il2Cpp.types[typeDef.byvalTypeIndex].type)
                                    }
                                    parameterStrs.add("$thisType __this")
                                } else if (il2Cpp.version <= 24) {
                                    methodTypeSignature.add(Il2CppTypeEnum.PTR)
                                    parameterStrs.add("Il2CppObject* __this")
                                }
                                for (j in 0 until methodDef.parameterCount) {
                                    val parameterDef = metadata.parameterDefs[methodDef.parameterStart + j]
                                    val parameterName = metadata.getStringFromIndex(parameterDef.nameIndex)
                                    val parameterType = il2Cpp.types[parameterDef.typeIndex]
                                    var parameterCType = parseType(parameterType, genericContext)
                                    if (parameterType.byref == 1) parameterCType += "*"
                                    methodTypeSignature.add(if (parameterType.byref == 1) Il2CppTypeEnum.PTR else parameterType.type)
                                    parameterStrs.add("$parameterCType ${fixName(parameterName)}")
                                }
                                methodTypeSignature.add(Il2CppTypeEnum.PTR)
                                parameterStrs.add("const $methodInfoName* method")
                                signature += parameterStrs.joinToString(", ")
                                signature += ");"
                                scriptMethod.signature = signature
                                scriptMethod.typeSignature = getMethodTypeSignature(methodTypeSignature)
                            }
                        }
                    }
                }
                typeIndex++
            }
        }
        // function ranges
        val ordered = ArrayList<Long>()
        if (il2Cpp.version >= 24.2) {
            for ((_, ptrs) in il2Cpp.codeGenModuleMethodPointers) ptrs.forEach { ordered.add(it) }
        } else {
            il2Cpp.methodPointers.forEach { ordered.add(it) }
        }
        il2Cpp.genericMethodPointers.forEach { ordered.add(it) }
        il2Cpp.invokerPointers.forEach { ordered.add(it) }
        if (il2Cpp.version < 29) {
            executor.customAttributeGenerators?.forEach { ordered.add(it) }
                ?: throw NullPointerException("customAttributeGenerators")
        }
        if (il2Cpp.version >= 22) {
            il2Cpp.reversePInvokeWrappers?.forEach { ordered.add(it) }
            il2Cpp.unresolvedVirtualCallPointers?.forEach { ordered.add(it) }
        }
        val distinct = LinkedHashSet(ordered).toMutableList()
        distinct.sortWith { a, b -> java.lang.Long.compareUnsigned(a, b) }
        distinct.remove(0L)
        json.addresses = LongArray(distinct.size) { il2Cpp.getRVA(distinct[it]) }
        // MetadataUsage
        progress.onProgress("metadata usage", -1f)
        if (il2Cpp.version >= 27) {
            val sectionHelper = executor.getSectionHelper()
            for (sec in sectionHelper.data) {
                il2Cpp.position = sec.offset
                val end = umin(sec.offsetEnd, il2Cpp.length) - il2Cpp.pointerSize
                while (ult(il2Cpp.position, end)) {
                    val addr = il2Cpp.position
                    val metadataValue = il2Cpp.readUIntPtr()
                    val position = il2Cpp.position
                    if (ult(metadataValue, 0xFFFFFFFFL)) {
                        val encodedToken = metadataValue and 0xFFFFFFFFL
                        val usage = Metadata.getEncodedIndexType(encodedToken)
                        if (usage > 0 && usage <= 6) {
                            val decodedIndex = metadata.getDecodedMethodIndex(encodedToken)
                            if (metadataValue == (((usage shl 29) or (decodedIndex shl 1)) + 1) and 0xFFFFFFFFL) {
                                val va = il2Cpp.mapRTVA(addr)
                                if (va != 0L) {
                                    when (usage.toInt()) {
                                        Il2CppMetadataUsage.TypeInfo -> if (decodedIndex < il2Cpp.types.size) addMetadataUsageTypeInfo(json, decodedIndex, va)
                                        Il2CppMetadataUsage.Il2CppType -> if (decodedIndex < il2Cpp.types.size) addMetadataUsageIl2CppType(json, decodedIndex, va)
                                        Il2CppMetadataUsage.MethodDef -> if (decodedIndex < metadata.methodDefs.size) addMetadataUsageMethodDef(json, decodedIndex, va)
                                        Il2CppMetadataUsage.FieldInfo -> if (decodedIndex < metadata.fieldRefs.size) addMetadataUsageFieldInfo(json, decodedIndex, va)
                                        Il2CppMetadataUsage.StringLiteral -> if (decodedIndex < metadata.stringLiterals.size) addMetadataUsageStringLiteral(json, decodedIndex, va)
                                        Il2CppMetadataUsage.MethodRef -> if (decodedIndex < il2Cpp.methodSpecs.size) addMetadataUsageMethodRef(json, decodedIndex, va)
                                    }
                                    if (il2Cpp.position != position) il2Cpp.position = position
                                }
                            }
                        }
                    }
                }
            }
        } else if (il2Cpp.version > 16 && il2Cpp.version < 27) {
            val dic = metadata.metadataUsageDic!!
            for ((k, v) in dic[Il2CppMetadataUsage.TypeInfo]!!) addMetadataUsageTypeInfo(json, v, il2Cpp.metadataUsages[k.toInt()])
            for ((k, v) in dic[Il2CppMetadataUsage.Il2CppType]!!) addMetadataUsageIl2CppType(json, v, il2Cpp.metadataUsages[k.toInt()])
            for ((k, v) in dic[Il2CppMetadataUsage.MethodDef]!!) addMetadataUsageMethodDef(json, v, il2Cpp.metadataUsages[k.toInt()])
            for ((k, v) in dic[Il2CppMetadataUsage.FieldInfo]!!) addMetadataUsageFieldInfo(json, v, il2Cpp.metadataUsages[k.toInt()])
            for ((k, v) in dic[Il2CppMetadataUsage.StringLiteral]!!) addMetadataUsageStringLiteral(json, v, il2Cpp.metadataUsages[k.toInt()])
            for ((k, v) in dic[Il2CppMetadataUsage.MethodRef]!!) addMetadataUsageMethodRef(json, v, il2Cpp.metadataUsages[k.toInt()])
        }
        // stringliteral.json
        progress.onProgress("writing json", -1f)
        Utf8Writer(FileOutputStream(File(outputDir, "stringliteral.json"))).use { out ->
            val w = JsonWriter(out)
            w.beginArray()
            for (s in json.scriptString) {
                w.beginObject()
                w.name("value").value(s.value)
                w.name("address").value("0x${hex(s.address)}")
                w.endObject()
            }
            w.endArray()
        }
        Utf8Writer(FileOutputStream(File(outputDir, "script.json"))).use { out -> json.write(JsonWriter(out)) }
        lastScriptJson = json
        // il2cpp.h
        progress.onProgress("il2cpp.h", -1f)
        var i = 0
        while (i < genericClassList.size) {
            addGenericClassStruct(genericClassList[i])
            i++
        }
        for (info in structInfoList) structInfoWithStructName.putIfAbsent(info.typeName + "_o", info)
        val header = when (il2Cpp.version) {
            22.0 -> HeaderConstants.HeaderV22
            23.0, 24.0 -> HeaderConstants.HeaderV240
            24.1 -> HeaderConstants.HeaderV241
            24.2, 24.3, 24.4, 24.5 -> HeaderConstants.HeaderV242
            27.0, 27.1, 27.2 -> HeaderConstants.HeaderV27
            29.0, 29.1, 31.0 -> HeaderConstants.HeaderV29
            else -> {
                logger.log("WARNING: This il2cpp version [${Il2Cpp.fmtVersion(il2Cpp.version)}] does not support generating .h files")
                return
            }
        }
        val headerFile = File(outputDir, "il2cpp.h")
        try {
            Utf8Writer(FileOutputStream(headerFile)).use { out ->
                out.write(HeaderConstants.GenericHeader)
                out.write(header)
                for (info in structInfoList) {
                    progress.checkCancelled()
                    out.append(recursionStructInfo(info))
                }
                out.append(arrayClassHeader)
                out.append(methodInfoHeader)
            }
        } catch (e: Exception) {
            headerFile.delete()
            throw e
        }
    }

    private fun addMetadataUsageTypeInfo(json: ScriptJson, index: Long, address: Long) {
        val type = il2Cpp.types[index.toInt()]
        val typeName = executor.getTypeName(type, true, false)
        val scriptMetadata = ScriptMetadata()
        json.scriptMetadata.add(scriptMetadata)
        scriptMetadata.address = il2Cpp.getRVA(address)
        scriptMetadata.name = typeName + "_TypeInfo"
        val signature = getIl2CppStructName(type)
        scriptMetadata.signature = if (signature.endsWith("_array")) "Il2CppClass*" else fixName(signature) + "_c*"
    }

    private fun addMetadataUsageIl2CppType(json: ScriptJson, index: Long, address: Long) {
        val type = il2Cpp.types[index.toInt()]
        val typeName = executor.getTypeName(type, true, false)
        val scriptMetadata = ScriptMetadata()
        json.scriptMetadata.add(scriptMetadata)
        scriptMetadata.address = il2Cpp.getRVA(address)
        scriptMetadata.name = typeName + "_var"
        scriptMetadata.signature = "Il2CppType*"
    }

    private fun addMetadataUsageMethodDef(json: ScriptJson, index: Long, address: Long) {
        val methodDef = metadata.methodDefs[index.toInt()]
        val typeDef = metadata.typeDefs[methodDef.declaringType]
        val typeName = executor.getTypeDefName(typeDef, true, true)
        val methodName = typeName + "." + metadata.getStringFromIndex(methodDef.nameIndex) + "()"
        val scriptMetadataMethod = ScriptMetadataMethod()
        json.scriptMetadataMethod.add(scriptMetadataMethod)
        scriptMetadataMethod.address = il2Cpp.getRVA(address)
        scriptMetadataMethod.name = "Method\$" + methodName
        val imageName = typeDefImageNames[typeDef] ?: throw NoSuchElementException("imageName")
        val methodPointer = il2Cpp.getMethodPointer(imageName, methodDef)
        if (methodPointer != 0L) scriptMetadataMethod.methodAddress = il2Cpp.getRVA(methodPointer)
    }

    private fun addMetadataUsageFieldInfo(json: ScriptJson, index: Long, address: Long) {
        val fieldRef = metadata.fieldRefs[index.toInt()]
        val type = il2Cpp.types[fieldRef.typeIndex]
        val typeDef = getTypeDefinition(type)!!
        val fieldDef = metadata.fieldDefs[typeDef.fieldStart + fieldRef.fieldIndex]
        val fieldName = executor.getTypeName(type, true, false) + "." + metadata.getStringFromIndex(fieldDef.nameIndex)
        val scriptMetadata = ScriptMetadata()
        json.scriptMetadata.add(scriptMetadata)
        scriptMetadata.address = il2Cpp.getRVA(address)
        scriptMetadata.name = "Field\$" + fieldName
    }

    private fun addMetadataUsageStringLiteral(json: ScriptJson, index: Long, address: Long) {
        val scriptString = ScriptString()
        json.scriptString.add(scriptString)
        scriptString.address = il2Cpp.getRVA(address)
        scriptString.value = metadata.getStringLiteralFromIndex(index)
    }

    private fun addMetadataUsageMethodRef(json: ScriptJson, index: Long, address: Long) {
        val methodSpec = il2Cpp.methodSpecs[index.toInt()]
        val scriptMetadataMethod = ScriptMetadataMethod()
        json.scriptMetadataMethod.add(scriptMetadataMethod)
        scriptMetadataMethod.address = il2Cpp.getRVA(address)
        val (methodSpecTypeName, methodSpecMethodName) = executor.getMethodSpecName(methodSpec, true)
        scriptMetadataMethod.name = "Method\$" + methodSpecTypeName + "." + methodSpecMethodName + "()"
        val genericMethodPointer = il2Cpp.methodSpecGenericMethodPointers[methodSpec]
        if (genericMethodPointer != null && genericMethodPointer != 0L) {
            scriptMetadataMethod.methodAddress = il2Cpp.getRVA(genericMethodPointer)
        }
    }

    private fun parseType(il2CppType: Il2CppType?, context: Il2CppGenericContext? = null): String {
        il2CppType ?: throw NullPointerException("il2CppType")
        when (il2CppType.type) {
            Il2CppTypeEnum.VOID -> return "void"
            Il2CppTypeEnum.BOOLEAN -> return "bool"
            Il2CppTypeEnum.CHAR -> return "uint16_t"
            Il2CppTypeEnum.I1 -> return "int8_t"
            Il2CppTypeEnum.U1 -> return "uint8_t"
            Il2CppTypeEnum.I2 -> return "int16_t"
            Il2CppTypeEnum.U2 -> return "uint16_t"
            Il2CppTypeEnum.I4 -> return "int32_t"
            Il2CppTypeEnum.U4 -> return "uint32_t"
            Il2CppTypeEnum.I8 -> return "int64_t"
            Il2CppTypeEnum.U8 -> return "uint64_t"
            Il2CppTypeEnum.R4 -> return "float"
            Il2CppTypeEnum.R8 -> return "double"
            Il2CppTypeEnum.STRING -> return "System_String_o*"
            Il2CppTypeEnum.PTR -> {
                val oriType = il2Cpp.getIl2CppType(il2CppType.dataType)
                return parseType(oriType) + "*"
            }
            Il2CppTypeEnum.VALUETYPE -> {
                val typeDef = executor.getTypeDefinitionFromIl2CppType(il2CppType)
                if (typeDef.isEnum) return parseType(il2Cpp.types[typeDef.elementTypeIndex])
                return structName(typeDef) + "_o"
            }
            Il2CppTypeEnum.CLASS -> {
                val typeDef = executor.getTypeDefinitionFromIl2CppType(il2CppType)
                return structName(typeDef) + "_o*"
            }
            Il2CppTypeEnum.VAR -> {
                if (context != null) return parseVar(il2CppType, context.classInst)
                return "Il2CppObject*"
            }
            Il2CppTypeEnum.ARRAY -> {
                val arrayType = il2Cpp.mapArrayType(il2CppType.array)
                val elementType = il2Cpp.getIl2CppType(arrayType.etype)
                val elementStructName = getIl2CppStructName(elementType, context)
                val typeStructName = elementStructName + "_array"
                if (structNameHashSet.add(typeStructName)) parseArrayClassStruct(elementType, context)
                return "$typeStructName*"
            }
            Il2CppTypeEnum.GENERICINST -> {
                val genericClass = il2Cpp.mapGenericClass(il2CppType.genericClass)
                val typeDef = executor.getGenericClassTypeDefinition(genericClass)
                val typeStructName = genericClassStructNameDic[il2CppType.genericClass]
                    ?: throw NoSuchElementException("The given key was not present in the dictionary.")
                if (structNameHashSet.add(typeStructName)) genericClassList.add(il2CppType.genericClass)
                typeDef!!
                if (typeDef.isValueType) {
                    if (typeDef.isEnum) return parseType(il2Cpp.types[typeDef.elementTypeIndex])
                    return typeStructName + "_o"
                }
                return typeStructName + "_o*"
            }
            Il2CppTypeEnum.TYPEDBYREF -> return "Il2CppObject*"
            Il2CppTypeEnum.I -> return "intptr_t"
            Il2CppTypeEnum.U -> return "uintptr_t"
            Il2CppTypeEnum.OBJECT -> return "Il2CppObject*"
            Il2CppTypeEnum.SZARRAY -> {
                val elementType = il2Cpp.getIl2CppType(il2CppType.dataType)
                val elementStructName = getIl2CppStructName(elementType, context)
                val typeStructName = elementStructName + "_array"
                if (structNameHashSet.add(typeStructName)) parseArrayClassStruct(elementType, context)
                return "$typeStructName*"
            }
            Il2CppTypeEnum.MVAR -> {
                if (context != null) {
                    executor.getGenericParameteFromIl2CppType(il2CppType)
                    // https://github.com/Perfare/Il2CppDumper/issues/687
                    if (context.methodInst == 0L && context.classInst != 0L) {
                        return parseVar(il2CppType, context.classInst)
                    }
                    return parseVar(il2CppType, context.methodInst)
                }
                return "Il2CppObject*"
            }
            else -> throw UnsupportedOperationException("Specified method is not supported.")
        }
    }

    private fun resolveGenericArgument(il2CppType: Il2CppType, inst: Long): Il2CppType? {
        val genericParameter = executor.getGenericParameteFromIl2CppType(il2CppType)
        val genericInst = il2Cpp.mapGenericInst(inst)
        val pointers = il2Cpp.mapVATRPointers(genericInst.typeArgv, genericInst.typeArgc)
        val pointer = pointers[genericParameter.num]
        return il2Cpp.getIl2CppType(pointer)
    }

    private fun parseVar(il2CppType: Il2CppType, inst: Long): String = parseType(resolveGenericArgument(il2CppType, inst))

    private fun getMethodTypeSignature(types: List<Int>): String {
        val sb = StringBuilder()
        for (type in types) {
            sb.append(
                when (type) {
                    Il2CppTypeEnum.VOID -> "v"
                    Il2CppTypeEnum.BOOLEAN, Il2CppTypeEnum.CHAR, Il2CppTypeEnum.I1, Il2CppTypeEnum.U1, Il2CppTypeEnum.I2,
                    Il2CppTypeEnum.U2, Il2CppTypeEnum.I4, Il2CppTypeEnum.U4 -> "i"
                    Il2CppTypeEnum.I8, Il2CppTypeEnum.U8 -> "j"
                    Il2CppTypeEnum.R4 -> "f"
                    Il2CppTypeEnum.R8 -> "d"
                    Il2CppTypeEnum.STRING, Il2CppTypeEnum.PTR, Il2CppTypeEnum.VALUETYPE, Il2CppTypeEnum.CLASS, Il2CppTypeEnum.VAR,
                    Il2CppTypeEnum.ARRAY, Il2CppTypeEnum.GENERICINST, Il2CppTypeEnum.TYPEDBYREF, Il2CppTypeEnum.I, Il2CppTypeEnum.U,
                    Il2CppTypeEnum.OBJECT, Il2CppTypeEnum.SZARRAY, Il2CppTypeEnum.MVAR -> "i"
                    else -> throw UnsupportedOperationException("Specified method is not supported.")
                }
            )
        }
        return sb.toString()
    }

    private fun addStruct(typeDef: Il2CppTypeDefinition) {
        val structInfo = StructInfo()
        structInfoList.add(structInfo)
        structInfo.typeName = structName(typeDef)
        structInfo.isValueType = typeDef.isValueType
        addParents(typeDef, structInfo)
        addFields(typeDef, structInfo, null)
        addVTableMethod(structInfo, typeDef)
        addRGCTX(structInfo, typeDef)
    }

    private fun addGenericClassStruct(pointer: Long) {
        val genericClass = il2Cpp.mapGenericClass(pointer)
        val typeDef = executor.getGenericClassTypeDefinition(genericClass)!!
        val structInfo = StructInfo()
        structInfoList.add(structInfo)
        structInfo.typeName = genericClassStructNameDic[pointer]!!
        structInfo.isValueType = typeDef.isValueType
        addParents(typeDef, structInfo)
        addFields(typeDef, structInfo, genericClass.context)
        addVTableMethod(structInfo, typeDef)
    }

    private fun addParents(typeDef: Il2CppTypeDefinition, structInfo: StructInfo) {
        if (!typeDef.isValueType && !typeDef.isEnum) {
            if (typeDef.parentIndex >= 0) {
                val parent = il2Cpp.types[typeDef.parentIndex]
                if (parent.type != Il2CppTypeEnum.OBJECT) {
                    structInfo.parent = getIl2CppStructName(parent)
                }
            }
        }
    }

    private fun addFields(typeDef: Il2CppTypeDefinition, structInfo: StructInfo, context: Il2CppGenericContext?) {
        if (typeDef.fieldCount > 0) {
            val fieldEnd = typeDef.fieldStart + typeDef.fieldCount
            val cache = HashSet<String>()
            for (i in typeDef.fieldStart until fieldEnd) {
                val fieldDef = metadata.fieldDefs[i]
                val fieldType = il2Cpp.types[fieldDef.typeIndex]
                if ((fieldType.attrs and FIELD_ATTRIBUTE_LITERAL) != 0) continue
                val structFieldInfo = StructFieldInfo(fieldTypeName = parseType(fieldType, context))
                var fieldName = fixName(metadata.getStringFromIndex(fieldDef.nameIndex))
                if (!cache.add(fieldName)) {
                    fieldName = "_${i - typeDef.fieldStart}_$fieldName"
                }
                structFieldInfo.fieldName = fieldName
                structFieldInfo.isValueType = isValueType(fieldType, context)
                structFieldInfo.isCustomType = isCustomType(fieldType, context)
                if ((fieldType.attrs and FIELD_ATTRIBUTE_STATIC) != 0) {
                    structInfo.staticFields.add(structFieldInfo)
                } else {
                    structInfo.fields.add(structFieldInfo)
                }
            }
        }
    }

    private fun addVTableMethod(structInfo: StructInfo, typeDef: Il2CppTypeDefinition) {
        val dic = java.util.TreeMap<Int, Il2CppMethodDefinition>()
        for (i in 0 until typeDef.vtableCount) {
            val vTableIndex = typeDef.vtableStart + i
            val encodedMethodIndex = metadata.vtableMethods[vTableIndex]
            val usage = Metadata.getEncodedIndexType(encodedMethodIndex)
            val index = metadata.getDecodedMethodIndex(encodedMethodIndex)
            val methodDef = if (usage == 6L) {
                val methodSpec = il2Cpp.methodSpecs[index.toInt()]
                metadata.methodDefs[methodSpec.methodDefinitionIndex]
            } else {
                metadata.methodDefs[index.toInt()]
            }
            if (methodDef.slot != 0xFFFF) dic[methodDef.slot] = methodDef
        }
        if (dic.isNotEmpty()) {
            val arr = arrayOfNulls<StructVTableMethodInfo>(dic.lastKey() + 1)
            for ((slot, methodDef) in dic) {
                arr[slot] = StructVTableMethodInfo(fixName(metadata.getStringFromIndex(methodDef.nameIndex)))
            }
            structInfo.vTableMethod = arr
        }
    }

    private fun rgctxInfo(definitionData: dev.il2cpp.dumper.core.il2cpp.Il2CppRGCTXDefinition): StructRGCTXInfo {
        val structRGCTXInfo = StructRGCTXInfo()
        structRGCTXInfo.type = definitionData.type
        val rgctxDefData: Int = if (il2Cpp.version >= 27.2) {
            il2Cpp.position = il2Cpp.mapVATR(definitionData.dataPtr)
            il2Cpp.readInt32()
        } else {
            definitionData.data
        }
        when (definitionData.type) {
            Il2CppRGCTXDataType.TYPE -> {
                val il2CppType = il2Cpp.types[rgctxDefData]
                structRGCTXInfo.typeName = fixName(executor.getTypeName(il2CppType, true, false))
            }
            Il2CppRGCTXDataType.CLASS -> {
                val il2CppType = il2Cpp.types[rgctxDefData]
                structRGCTXInfo.className = fixName(executor.getTypeName(il2CppType, true, false))
            }
            Il2CppRGCTXDataType.METHOD -> {
                val methodSpec = il2Cpp.methodSpecs[rgctxDefData]
                val (methodSpecTypeName, methodSpecMethodName) = executor.getMethodSpecName(methodSpec, true)
                structRGCTXInfo.methodName = fixName("$methodSpecTypeName.$methodSpecMethodName")
            }
        }
        return structRGCTXInfo
    }

    private fun addRGCTX(structInfo: StructInfo, typeDef: Il2CppTypeDefinition) {
        val imageName = typeDefImageNames[typeDef]!!
        val collection = executor.getRGCTXDefinition(imageName, typeDef) ?: return
        for (definitionData in collection) structInfo.rgctxs.add(rgctxInfo(definitionData))
    }

    private fun generateRGCTX(imageName: String, methodDef: Il2CppMethodDefinition): List<StructRGCTXInfo> {
        val rgctxs = ArrayList<StructRGCTXInfo>()
        val collection = executor.getRGCTXDefinition(imageName, methodDef) ?: return rgctxs
        for (definitionData in collection) rgctxs.add(rgctxInfo(definitionData))
        return rgctxs
    }

    private fun parseArrayClassStruct(il2CppType: Il2CppType?, context: Il2CppGenericContext?) {
        val structName = getIl2CppStructName(il2CppType, context)
        arrayClassHeader.append("struct ${structName}_array {\n")
            .append("\tIl2CppObject obj;\n")
            .append("\tIl2CppArrayBounds *bounds;\n")
            .append("\til2cpp_array_size_t max_length;\n")
            .append("\t${parseType(il2CppType, context)} m_Items[65535];\n")
            .append("};\n")
    }

    private fun getTypeDefinition(il2CppType: Il2CppType): Il2CppTypeDefinition? {
        return when (il2CppType.type) {
            Il2CppTypeEnum.VOID, Il2CppTypeEnum.BOOLEAN, Il2CppTypeEnum.CHAR, Il2CppTypeEnum.I1, Il2CppTypeEnum.U1,
            Il2CppTypeEnum.I2, Il2CppTypeEnum.U2, Il2CppTypeEnum.I4, Il2CppTypeEnum.U4, Il2CppTypeEnum.I8, Il2CppTypeEnum.U8,
            Il2CppTypeEnum.R4, Il2CppTypeEnum.R8, Il2CppTypeEnum.STRING, Il2CppTypeEnum.TYPEDBYREF, Il2CppTypeEnum.I,
            Il2CppTypeEnum.U, Il2CppTypeEnum.VALUETYPE, Il2CppTypeEnum.CLASS, Il2CppTypeEnum.OBJECT ->
                executor.getTypeDefinitionFromIl2CppType(il2CppType)
            Il2CppTypeEnum.GENERICINST -> {
                val genericClass = il2Cpp.mapGenericClass(il2CppType.genericClass)
                executor.getGenericClassTypeDefinition(genericClass)
            }
            else -> throw UnsupportedOperationException("Specified method is not supported.")
        }
    }

    private fun createStructNameDic(typeDef: Il2CppTypeDefinition) {
        val typeName = executor.getTypeDefName(typeDef, true, true)
        val typeStructName = fixName(typeName)
        val uniqueName = getUniqueName(typeStructName)
        structNameDic[typeDef] = uniqueName
    }

    private fun getUniqueName(name: String): String {
        var fixName = name
        var i = 1
        while (!structNameHashSet.add(fixName)) {
            fixName = "${name}_${i++}"
        }
        return fixName
    }

    private fun recursionStructInfo(info: StructInfo): String {
        if (!structCache.add(info)) return ""
        val sb = StringBuilder()
        val pre = StringBuilder()
        if (info.parent != null) {
            val parentStructName = info.parent + "_o"
            pre.append(recursionStructInfo(structInfoWithStructName[parentStructName] ?: throw NoSuchElementException(parentStructName)))
            sb.append("struct ${info.typeName}_Fields : ${info.parent}_Fields {\n")
        } else {
            if (il2Cpp is PE && !info.isValueType) {
                if (il2Cpp.is32Bit) sb.append("struct __declspec(align(4)) ${info.typeName}_Fields {\n")
                else sb.append("struct __declspec(align(8)) ${info.typeName}_Fields {\n")
            } else {
                sb.append("struct ${info.typeName}_Fields {\n")
            }
        }
        for (field in info.fields) {
            if (field.isValueType) {
                val fieldInfo = structInfoWithStructName[field.fieldTypeName] ?: throw NoSuchElementException(field.fieldTypeName)
                pre.append(recursionStructInfo(fieldInfo))
            }
            if (field.isCustomType) sb.append("\tstruct ${field.fieldTypeName} ${field.fieldName};\n")
            else sb.append("\t${field.fieldTypeName} ${field.fieldName};\n")
        }
        sb.append("};\n")

        if (info.rgctxs.size > 0) {
            sb.append("struct ${info.typeName}_RGCTXs {\n")
            for (i in info.rgctxs.indices) {
                val rgctx = info.rgctxs[i]
                when (rgctx.type) {
                    Il2CppRGCTXDataType.TYPE -> sb.append("\tIl2CppType* _${i}_${rgctx.typeName};\n")
                    Il2CppRGCTXDataType.CLASS -> sb.append("\tIl2CppClass* _${i}_${rgctx.className};\n")
                    Il2CppRGCTXDataType.METHOD -> sb.append("\tMethodInfo* _${i}_${rgctx.methodName};\n")
                }
            }
            sb.append("};\n")
        }

        if (info.vTableMethod.isNotEmpty()) {
            sb.append("struct ${info.typeName}_VTable {\n")
            for (i in info.vTableMethod.indices) {
                sb.append("\tVirtualInvokeData _${i}_")
                val method = info.vTableMethod[i]
                sb.append(method?.methodName ?: "unknown")
                sb.append(";\n")
            }
            sb.append("};\n")
        }

        sb.append("struct ${info.typeName}_c {\n")
        sb.append("\tIl2CppClass_1 _1;\n")
        if (info.staticFields.size > 0) sb.append("\tstruct ${info.typeName}_StaticFields* static_fields;\n")
        else sb.append("\tvoid* static_fields;\n")
        if (info.rgctxs.size > 0) sb.append("\t${info.typeName}_RGCTXs* rgctx_data;\n")
        else sb.append("\tIl2CppRGCTXData* rgctx_data;\n")
        sb.append("\tIl2CppClass_2 _2;\n")
        if (info.vTableMethod.isNotEmpty()) sb.append("\t${info.typeName}_VTable vtable;\n")
        else sb.append("\tVirtualInvokeData vtable[32];\n")
        sb.append("};\n")

        sb.append("struct ${info.typeName}_o {\n")
        if (!info.isValueType) {
            sb.append("\t${info.typeName}_c *klass;\n")
            sb.append("\tvoid *monitor;\n")
        }
        sb.append("\t${info.typeName}_Fields fields;\n")
        sb.append("};\n")

        if (info.staticFields.size > 0) {
            sb.append("struct ${info.typeName}_StaticFields {\n")
            for (field in info.staticFields) {
                if (field.isValueType) {
                    val fieldInfo = structInfoWithStructName[field.fieldTypeName] ?: throw NoSuchElementException(field.fieldTypeName)
                    pre.append(recursionStructInfo(fieldInfo))
                }
                if (field.isCustomType) sb.append("\tstruct ${field.fieldTypeName} ${field.fieldName};\n")
                else sb.append("\t${field.fieldTypeName} ${field.fieldName};\n")
            }
            sb.append("};\n")
        }
        return pre.append(sb).toString()
    }

    private fun getIl2CppStructName(il2CppType: Il2CppType?, context: Il2CppGenericContext? = null): String {
        il2CppType ?: throw NullPointerException("il2CppType")
        when (il2CppType.type) {
            Il2CppTypeEnum.VOID, Il2CppTypeEnum.BOOLEAN, Il2CppTypeEnum.CHAR, Il2CppTypeEnum.I1, Il2CppTypeEnum.U1,
            Il2CppTypeEnum.I2, Il2CppTypeEnum.U2, Il2CppTypeEnum.I4, Il2CppTypeEnum.U4, Il2CppTypeEnum.I8, Il2CppTypeEnum.U8,
            Il2CppTypeEnum.R4, Il2CppTypeEnum.R8, Il2CppTypeEnum.STRING, Il2CppTypeEnum.TYPEDBYREF, Il2CppTypeEnum.I,
            Il2CppTypeEnum.U, Il2CppTypeEnum.VALUETYPE, Il2CppTypeEnum.CLASS, Il2CppTypeEnum.OBJECT -> {
                val typeDef = executor.getTypeDefinitionFromIl2CppType(il2CppType)
                return structName(typeDef)
            }
            Il2CppTypeEnum.PTR -> {
                val oriType = il2Cpp.getIl2CppType(il2CppType.dataType)
                return getIl2CppStructName(oriType)
            }
            Il2CppTypeEnum.ARRAY -> {
                val arrayType = il2Cpp.mapArrayType(il2CppType.array)
                val elementType = il2Cpp.getIl2CppType(arrayType.etype)
                val elementStructName = getIl2CppStructName(elementType, context)
                val typeStructName = elementStructName + "_array"
                if (structNameHashSet.add(typeStructName)) parseArrayClassStruct(elementType, context)
                return typeStructName
            }
            Il2CppTypeEnum.SZARRAY -> {
                val elementType = il2Cpp.getIl2CppType(il2CppType.dataType)
                val elementStructName = getIl2CppStructName(elementType, context)
                val typeStructName = elementStructName + "_array"
                if (structNameHashSet.add(typeStructName)) parseArrayClassStruct(elementType, context)
                return typeStructName
            }
            Il2CppTypeEnum.GENERICINST -> {
                val typeStructName = genericClassStructNameDic[il2CppType.genericClass]
                    ?: throw NoSuchElementException("The given key was not present in the dictionary.")
                if (structNameHashSet.add(typeStructName)) genericClassList.add(il2CppType.genericClass)
                return typeStructName
            }
            Il2CppTypeEnum.VAR -> {
                if (context != null) return getIl2CppStructName(resolveGenericArgument(il2CppType, context.classInst))
                return "System_Object"
            }
            Il2CppTypeEnum.MVAR -> {
                if (context != null) return getIl2CppStructName(resolveGenericArgument(il2CppType, context.methodInst))
                return "System_Object"
            }
            else -> throw UnsupportedOperationException("Specified method is not supported.")
        }
    }

    private fun isValueType(il2CppType: Il2CppType?, context: Il2CppGenericContext?): Boolean {
        il2CppType ?: throw NullPointerException("il2CppType")
        return when (il2CppType.type) {
            Il2CppTypeEnum.VALUETYPE -> !executor.getTypeDefinitionFromIl2CppType(il2CppType).isEnum
            Il2CppTypeEnum.GENERICINST -> {
                val genericClass = il2Cpp.mapGenericClass(il2CppType.genericClass)
                val typeDef = executor.getGenericClassTypeDefinition(genericClass)!!
                typeDef.isValueType && !typeDef.isEnum
            }
            Il2CppTypeEnum.VAR -> if (context != null) isValueType(resolveGenericArgument(il2CppType, context.classInst), null) else false
            Il2CppTypeEnum.MVAR -> if (context != null) isValueType(resolveGenericArgument(il2CppType, context.methodInst), null) else false
            else -> false
        }
    }

    private fun isCustomType(il2CppType: Il2CppType?, context: Il2CppGenericContext?): Boolean {
        il2CppType ?: throw NullPointerException("il2CppType")
        return when (il2CppType.type) {
            Il2CppTypeEnum.PTR -> isCustomType(il2Cpp.getIl2CppType(il2CppType.dataType), context)
            Il2CppTypeEnum.STRING, Il2CppTypeEnum.CLASS, Il2CppTypeEnum.ARRAY, Il2CppTypeEnum.SZARRAY -> true
            Il2CppTypeEnum.VALUETYPE -> {
                val typeDef = executor.getTypeDefinitionFromIl2CppType(il2CppType)
                if (typeDef.isEnum) isCustomType(il2Cpp.types[typeDef.elementTypeIndex], context) else true
            }
            Il2CppTypeEnum.GENERICINST -> {
                val genericClass = il2Cpp.mapGenericClass(il2CppType.genericClass)
                val typeDef = executor.getGenericClassTypeDefinition(genericClass)!!
                if (typeDef.isEnum) isCustomType(il2Cpp.types[typeDef.elementTypeIndex], context) else true
            }
            Il2CppTypeEnum.VAR -> if (context != null) isCustomType(resolveGenericArgument(il2CppType, context.classInst), null) else false
            Il2CppTypeEnum.MVAR -> if (context != null) isCustomType(resolveGenericArgument(il2CppType, context.methodInst), null) else false
            else -> false
        }
    }

    private fun generateMethodInfo(methodInfoName: String, structTypeName: String, rgctxs: List<StructRGCTXInfo>) {
        val h = methodInfoHeader
        if (rgctxs.isNotEmpty()) {
            h.append("struct ${methodInfoName}_RGCTXs {\n")
            for (i in rgctxs.indices) {
                val rgctx = rgctxs[i]
                when (rgctx.type) {
                    Il2CppRGCTXDataType.TYPE -> h.append("\tIl2CppType* _${i}_${rgctx.typeName};\n")
                    Il2CppRGCTXDataType.CLASS -> h.append("\tIl2CppClass* _${i}_${rgctx.className};\n")
                    Il2CppRGCTXDataType.METHOD -> h.append("\tMethodInfo* _${i}_${rgctx.methodName};\n")
                }
            }
            h.append("};\n")
        }
        h.append("struct $methodInfoName {\n")
        h.append("\tIl2CppMethodPointer methodPointer;\n")
        if (il2Cpp.version >= 29) {
            h.append("\tIl2CppMethodPointer virtualMethodPointer;\n")
            h.append("\tInvokerMethod invoker_method;\n")
        } else {
            h.append("\tvoid* invoker_method;\n")
        }
        h.append("\tconst char* name;\n")
        if (il2Cpp.version <= 24) h.append("\t${structTypeName}_c *declaring_type;\n")
        else h.append("\t${structTypeName}_c *klass;\n")
        h.append("\tconst Il2CppType *return_type;\n")
        if (il2Cpp.version >= 29) h.append("\tconst Il2CppType** parameters;\n")
        else h.append("\tconst void* parameters;\n")
        if (rgctxs.isNotEmpty()) h.append("\tconst ${methodInfoName}_RGCTXs* rgctx_data;\n")
        else h.append("\tconst Il2CppRGCTXData* rgctx_data;\n")
        h.append("\tunion\n")
        h.append("\t{\n")
        h.append("\t\tconst void* genericMethod;\n")
        if (il2Cpp.version >= 27) h.append("\t\tconst void* genericContainerHandle;\n")
        else h.append("\t\tconst void* genericContainer;\n")
        h.append("\t};\n")
        if (il2Cpp.version <= 24) h.append("\tint32_t customAttributeIndex;\n")
        h.append("\tuint32_t token;\n")
        h.append("\tuint16_t flags;\n")
        h.append("\tuint16_t iflags;\n")
        h.append("\tuint16_t slot;\n")
        h.append("\tuint8_t parameters_count;\n")
        h.append("\tuint8_t bitflags;\n")
        h.append("};\n")
    }

    companion object {
        private val keyword = hashSetOf(
            "klass", "monitor", "register", "_cs", "auto", "friend", "template", "flat", "default", "_ds", "interrupt",
            "unsigned", "signed", "asm", "if", "case", "break", "continue", "do", "new", "_", "short", "union", "class", "namespace"
        )
        private val specialKeywords = hashSetOf("inline", "near", "far")

        /** Port of FixName; operates on UTF-16 code units like .NET Regex. */
        fun fixName(input: String): String {
            var str = input
            if (keyword.contains(str)) {
                str = "_$str"
            } else if (specialKeywords.contains(str)) {
                str = "_${str}_"
            }
            if (str.isNotEmpty() && str[0] in '0'..'9') {
                return "_$str"
            }
            val sb = StringBuilder(str.length)
            for (c in str) {
                if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_') sb.append(c) else sb.append('_')
            }
            return sb.toString()
        }
    }
}
