package dev.il2cpp.dumper.core.il2cpp

import dev.il2cpp.dumper.core.DumpLogger
import dev.il2cpp.dumper.core.formats.WebAssemblyMemory
import dev.il2cpp.dumper.core.io.BinaryStream
import dev.il2cpp.dumper.core.utils.SectionHelper
import java.nio.ByteBuffer
import java.util.IdentityHashMap

/** Port of Il2Cpp.cs */
abstract class Il2Cpp(buffer: ByteBuffer) : BinaryStream(buffer) {
    var logger: DumpLogger = dev.il2cpp.dumper.core.Dumper.pendingLogger.get() ?: DumpLogger.NONE

    private lateinit var pMetadataRegistration: Il2CppMetadataRegistration
    private lateinit var pCodeRegistration: Il2CppCodeRegistration
    var methodPointers: LongArray = LongArray(0)
    var genericMethodPointers: LongArray = LongArray(0)
    var invokerPointers: LongArray = LongArray(0)
    var customAttributeGenerators: LongArray? = null
    var reversePInvokeWrappers: LongArray? = null
    var unresolvedVirtualCallPointers: LongArray? = null
    private var fieldOffsets: LongArray = LongArray(0)
    var types: Array<Il2CppType> = emptyArray()
    private val typeDic = HashMap<Long, Il2CppType>()
    var metadataUsages: LongArray = LongArray(0)
    private var genericMethodTable: List<Il2CppGenericMethodFunctionsDefinitions> = emptyList()
    var genericInstPointers: LongArray = LongArray(0)
    var genericInsts: Array<Il2CppGenericInst> = emptyArray()
    var methodSpecs: List<Il2CppMethodSpec> = emptyList()
    val methodDefinitionMethodSpecs = HashMap<Int, MutableList<Il2CppMethodSpec>>()
    val methodSpecGenericMethodPointers = IdentityHashMap<Il2CppMethodSpec, Long>()
    private var fieldOffsetsArePointers = false
    protected var metadataUsagesCount: Long = 0
    var codeGenModules: LinkedHashMap<String, Il2CppCodeGenModule> = LinkedHashMap()
    var codeGenModuleMethodPointers: LinkedHashMap<String, LongArray> = LinkedHashMap()
    var rgctxsDictionary: LinkedHashMap<String, HashMap<Long, List<Il2CppRGCTXDefinition>>> = LinkedHashMap()
    var isDumped = false

    /** Address of the code / metadata registration that was used, for the UI. */
    var codeRegistrationAddress = 0L
        private set
    var metadataRegistrationAddress = 0L
        private set

    abstract fun mapVATR(addr: Long): Long
    abstract fun mapRTVA(addr: Long): Long
    abstract fun search(): Boolean
    abstract fun plusSearch(methodCount: Int, typeDefinitionsCount: Int, imageCount: Int): Boolean
    abstract fun symbolSearch(): Boolean
    abstract fun getSectionHelper(methodCount: Int, typeDefinitionsCount: Int, imageCount: Int): SectionHelper
    abstract fun checkDump(): Boolean

    /** Short human readable description of the executable format. */
    abstract val formatName: String

    fun setProperties(version: Double, metadataUsagesCount: Long) {
        this.version = version
        this.metadataUsagesCount = metadataUsagesCount
    }

    protected fun autoPlusInit(codeRegistrationIn: Long, metadataRegistration: Long): Boolean {
        var codeRegistration = codeRegistrationIn
        if (codeRegistration != 0L) {
            val limit = if (this is WebAssemblyMemory) 0x35000L else 0x50000L
            if (version >= 24.2) {
                pCodeRegistration = readCodeRegistration(codeRegistration)
                if (version == 31.0) {
                    if (java.lang.Long.compareUnsigned(pCodeRegistration.genericMethodPointersCount, limit) > 0) {
                        codeRegistration -= pointerSize * 2
                    } else {
                        version = 29.0
                        logger.log("Change il2cpp version to: ${fmtVersion(version)}")
                    }
                }
                if (version == 29.0) {
                    if (java.lang.Long.compareUnsigned(pCodeRegistration.genericMethodPointersCount, limit) > 0) {
                        version = 29.1
                        codeRegistration -= pointerSize * 2
                        logger.log("Change il2cpp version to: ${fmtVersion(version)}")
                    }
                }
                if (version == 27.0) {
                    if (java.lang.Long.compareUnsigned(pCodeRegistration.reversePInvokeWrapperCount, limit) > 0) {
                        version = 27.1
                        codeRegistration -= pointerSize
                        logger.log("Change il2cpp version to: ${fmtVersion(version)}")
                    }
                }
                if (version == 24.4) {
                    codeRegistration -= pointerSize * 2
                    if (java.lang.Long.compareUnsigned(pCodeRegistration.reversePInvokeWrapperCount, limit) > 0) {
                        version = 24.5
                        codeRegistration -= pointerSize
                        logger.log("Change il2cpp version to: ${fmtVersion(version)}")
                    }
                }
                if (version == 24.2) {
                    if (pCodeRegistration.interopDataCount == 0L) {
                        version = 24.3
                        codeRegistration -= pointerSize * 2
                        logger.log("Change il2cpp version to: ${fmtVersion(version)}")
                    }
                }
            }
        }
        logger.log("CodeRegistration : ${java.lang.Long.toHexString(codeRegistration)}")
        logger.log("MetadataRegistration : ${java.lang.Long.toHexString(metadataRegistration)}")
        if (codeRegistration != 0L && metadataRegistration != 0L) {
            init(codeRegistration, metadataRegistration)
            return true
        }
        return false
    }

    private fun readCodeRegistration(addr: Long): Il2CppCodeRegistration {
        position = mapVATR(addr)
        return Il2CppCodeRegistration.read(this)
    }

    open fun init(codeRegistration: Long, metadataRegistration: Long) {
        codeRegistrationAddress = codeRegistration
        metadataRegistrationAddress = metadataRegistration
        pCodeRegistration = readCodeRegistration(codeRegistration)
        val limit = if (this is WebAssemblyMemory) 0x35000L else 0x50000L
        if (version == 27.0 && java.lang.Long.compareUnsigned(pCodeRegistration.invokerPointersCount, limit) > 0) {
            version = 27.1
            logger.log("Change il2cpp version to: ${fmtVersion(version)}")
            pCodeRegistration = readCodeRegistration(codeRegistration)
        }
        if (version == 27.1) {
            val pCodeGenModules = mapVATRPointers(pCodeRegistration.codeGenModules, pCodeRegistration.codeGenModulesCount)
            for (pCodeGenModule in pCodeGenModules) {
                val codeGenModule = readCodeGenModule(pCodeGenModule)
                if (codeGenModule.rgctxsCount > 0) {
                    val rgctxs = readRgctxs(codeGenModule.rgctxs, codeGenModule.rgctxsCount)
                    if (rgctxs.all { it.data.toLong() > limit }) {
                        version = 27.2
                        logger.log("Change il2cpp version to: ${fmtVersion(version)}")
                    }
                    break
                }
            }
        }
        if (version == 24.4 && java.lang.Long.compareUnsigned(pCodeRegistration.invokerPointersCount, limit) > 0) {
            version = 24.5
            logger.log("Change il2cpp version to: ${fmtVersion(version)}")
            pCodeRegistration = readCodeRegistration(codeRegistration)
        }
        if (version == 24.2 && pCodeRegistration.codeGenModules == 0L) {
            version = 24.3
            logger.log("Change il2cpp version to: ${fmtVersion(version)}")
            pCodeRegistration = readCodeRegistration(codeRegistration)
        }
        position = mapVATR(metadataRegistration)
        pMetadataRegistration = Il2CppMetadataRegistration.read(this)
        genericMethodPointers = mapVATRPointers(pCodeRegistration.genericMethodPointers, pCodeRegistration.genericMethodPointersCount)
        invokerPointers = mapVATRPointers(pCodeRegistration.invokerPointers, pCodeRegistration.invokerPointersCount)
        if (version < 27) {
            customAttributeGenerators = mapVATRPointers(pCodeRegistration.customAttributeGenerators, pCodeRegistration.customAttributeCount)
        }
        if (version > 16 && version < 27) {
            metadataUsages = mapVATRPointers(pMetadataRegistration.metadataUsages, metadataUsagesCount)
        }
        if (version >= 22) {
            if (pCodeRegistration.reversePInvokeWrapperCount != 0L)
                reversePInvokeWrappers = mapVATRPointers(pCodeRegistration.reversePInvokeWrappers, pCodeRegistration.reversePInvokeWrapperCount)
            if (pCodeRegistration.unresolvedVirtualCallCount != 0L)
                unresolvedVirtualCallPointers = mapVATRPointers(pCodeRegistration.unresolvedVirtualCallPointers, pCodeRegistration.unresolvedVirtualCallCount)
        }
        genericInstPointers = mapVATRPointers(pMetadataRegistration.genericInsts, pMetadataRegistration.genericInstsCount)
        genericInsts = Array(genericInstPointers.size) { i ->
            position = mapVATR(genericInstPointers[i])
            Il2CppGenericInst.read(this)
        }
        fieldOffsetsArePointers = version > 21
        if (version == 21.0) {
            val fieldTest = readUInt32Array(mapVATR(pMetadataRegistration.fieldOffsets), 6)
            fieldOffsetsArePointers = fieldTest[0] == 0L && fieldTest[1] == 0L && fieldTest[2] == 0L && fieldTest[3] == 0L && fieldTest[4] == 0L && fieldTest[5] > 0
        }
        fieldOffsets = if (fieldOffsetsArePointers) {
            mapVATRPointers(pMetadataRegistration.fieldOffsets, pMetadataRegistration.fieldOffsetsCount)
        } else {
            readUInt32Array(mapVATR(pMetadataRegistration.fieldOffsets), pMetadataRegistration.fieldOffsetsCount)
        }
        val pTypes = mapVATRPointers(pMetadataRegistration.types, pMetadataRegistration.typesCount)
        types = Array(pTypes.size) { i ->
            position = mapVATR(pTypes[i])
            val t = Il2CppType.read(this)
            t.init(version)
            typeDic.putIfAbsent(pTypes[i], t)
            t
        }
        if (version >= 24.2) {
            val pCodeGenModules = mapVATRPointers(pCodeRegistration.codeGenModules, pCodeRegistration.codeGenModulesCount)
            codeGenModules = LinkedHashMap(pCodeGenModules.size * 2)
            codeGenModuleMethodPointers = LinkedHashMap(pCodeGenModules.size * 2)
            rgctxsDictionary = LinkedHashMap(pCodeGenModules.size * 2)
            for (pCodeGenModule in pCodeGenModules) {
                val codeGenModule = readCodeGenModule(pCodeGenModule)
                val moduleName = readStringToNull(mapVATR(codeGenModule.moduleName))
                if (codeGenModules.containsKey(moduleName)) {
                    logger.log("WARNING: duplicate CodeGenModule $moduleName ignored")
                    continue
                }
                codeGenModules[moduleName] = codeGenModule
                val modulePointers: LongArray = try {
                    mapVATRPointers(codeGenModule.methodPointers, codeGenModule.methodPointerCount)
                } catch (e: Exception) {
                    if (codeGenModule.methodPointerCount < 0 || codeGenModule.methodPointerCount > Int.MAX_VALUE) throw e
                    LongArray(codeGenModule.methodPointerCount.toInt())
                }
                codeGenModuleMethodPointers[moduleName] = modulePointers

                val rgctxsDefDictionary = HashMap<Long, List<Il2CppRGCTXDefinition>>()
                rgctxsDictionary[moduleName] = rgctxsDefDictionary
                if (codeGenModule.rgctxsCount > 0) {
                    val rgctxs = readRgctxs(codeGenModule.rgctxs, codeGenModule.rgctxsCount)
                    position = mapVATR(codeGenModule.rgctxRanges)
                    val rgctxRanges = readArray(position, codeGenModule.rgctxRangesCount, 12) { Il2CppTokenRangePair.read(this) }
                    for (rgctxRange in rgctxRanges) {
                        if (rgctxRange.start < 0 || rgctxRange.length < 0 || rgctxRange.start + rgctxRange.length > rgctxs.size) {
                            throw IndexOutOfBoundsException("rgctx range")
                        }
                        val defs = rgctxs.subList(rgctxRange.start, rgctxRange.start + rgctxRange.length)
                        rgctxsDefDictionary.putIfAbsent(rgctxRange.token, defs)
                    }
                }
            }
        } else {
            methodPointers = mapVATRPointers(pCodeRegistration.methodPointers, pCodeRegistration.methodPointersCount)
        }
        position = mapVATR(pMetadataRegistration.genericMethodTable)
        genericMethodTable = readArray(position, pMetadataRegistration.genericMethodTableCount, 12) { Il2CppGenericMethodFunctionsDefinitions.read(this) }
        position = mapVATR(pMetadataRegistration.methodSpecs)
        methodSpecs = readArray(position, pMetadataRegistration.methodSpecsCount, 12) { Il2CppMethodSpec.read(this) }
        for (table in genericMethodTable) {
            val methodSpec = methodSpecs[table.genericMethodIndex]
            val methodDefinitionIndex = methodSpec.methodDefinitionIndex
            if (methodSpecGenericMethodPointers.containsKey(methodSpec)) continue
            val list = methodDefinitionMethodSpecs.getOrPut(methodDefinitionIndex) { ArrayList() }
            list.add(methodSpec)
            methodSpecGenericMethodPointers[methodSpec] = genericMethodPointers[table.indices.methodIndex]
        }
    }

    protected fun readCodeGenModule(addr: Long): Il2CppCodeGenModule {
        position = mapVATR(addr)
        return Il2CppCodeGenModule.read(this)
    }

    protected fun readRgctxs(addr: Long, count: Long): List<Il2CppRGCTXDefinition> {
        return readArray(mapVATR(addr), count, 4) { Il2CppRGCTXDefinition.read(this) }
    }

    /** MapVATR<ulong>(addr, count) */
    fun mapVATRPointers(addr: Long, count: Long): LongArray = readUIntPtrArray(mapVATR(addr), count)

    fun mapGenericClass(addr: Long): Il2CppGenericClass {
        position = mapVATR(addr)
        return Il2CppGenericClass.read(this)
    }

    fun mapGenericInst(addr: Long): Il2CppGenericInst {
        position = mapVATR(addr)
        return Il2CppGenericInst.read(this)
    }

    fun mapArrayType(addr: Long): Il2CppArrayType {
        position = mapVATR(addr)
        return Il2CppArrayType.read(this)
    }

    fun getFieldOffsetFromIndex(typeIndex: Int, fieldIndexInType: Int, fieldIndex: Int, isValueType: Boolean, isStatic: Boolean): Int {
        try {
            var offset = -1
            if (fieldOffsetsArePointers) {
                val ptr = fieldOffsets[typeIndex]
                if (ptr != 0L) {
                    position = mapVATR(ptr) + 4L * fieldIndexInType
                    offset = readInt32()
                }
            } else {
                offset = fieldOffsets[fieldIndex].toInt()
            }
            if (offset > 0) {
                if (isValueType && !isStatic) {
                    offset -= if (is32Bit) 8 else 16
                }
            }
            return offset
        } catch (e: Exception) {
            return -1
        }
    }

    fun getIl2CppType(pointer: Long): Il2CppType? = typeDic[pointer]

    fun getMethodPointer(imageName: String, methodDef: Il2CppMethodDefinition): Long {
        if (version >= 24.2) {
            val methodToken = methodDef.token
            val ptrs = codeGenModuleMethodPointers[imageName]
                ?: throw NoSuchElementException("The given key '$imageName' was not present in the dictionary.")
            val methodPointerIndex = methodToken and 0x00FFFFFFL
            return ptrs[(methodPointerIndex - 1).toInt()]
        } else {
            val methodIndex = methodDef.methodIndex
            if (methodIndex >= 0) {
                return methodPointers[methodIndex]
            }
        }
        return 0L
    }

    open fun getRVA(pointer: Long): Long = pointer

    companion object {
        /** Formats a version like .NET double.ToString(): 27 / 27.1 / 24.5 */
        fun fmtVersion(v: Double): String {
            return if (v == Math.floor(v)) v.toLong().toString() else v.toString()
        }
    }
}
