package dev.il2cpp.dumper.core.il2cpp

import dev.il2cpp.dumper.core.io.BinaryStream

/*
 * Port of Il2CppClass.cs. In the original every long/ulong field is read through
 * ReadIntPtr/ReadUIntPtr, i.e. it is pointer sized.
 */

class Il2CppCodeRegistration {
    var methodPointersCount = 0L
    var methodPointers = 0L
    var delegateWrappersFromNativeToManagedCount = 0L
    var delegateWrappersFromNativeToManaged = 0L
    var reversePInvokeWrapperCount = 0L
    var reversePInvokeWrappers = 0L
    var delegateWrappersFromManagedToNativeCount = 0L
    var delegateWrappersFromManagedToNative = 0L
    var marshalingFunctionsCount = 0L
    var marshalingFunctions = 0L
    var ccwMarshalingFunctionsCount = 0L
    var ccwMarshalingFunctions = 0L
    var genericMethodPointersCount = 0L
    var genericMethodPointers = 0L
    var genericAdjustorThunks = 0L
    var invokerPointersCount = 0L
    var invokerPointers = 0L
    var customAttributeCount = 0L
    var customAttributeGenerators = 0L
    var guidCount = 0L
    var guids = 0L
    var unresolvedVirtualCallCount = 0L
    var unresolvedVirtualCallPointers = 0L
    var unresolvedInstanceCallPointers = 0L
    var unresolvedStaticCallPointers = 0L
    var interopDataCount = 0L
    var interopData = 0L
    var windowsRuntimeFactoryCount = 0L
    var windowsRuntimeFactoryTable = 0L
    var codeGenModulesCount = 0L
    var codeGenModules = 0L

    companion object {
        fun read(s: BinaryStream): Il2CppCodeRegistration {
            val ver = s.version
            val c = Il2CppCodeRegistration()
            if (v(ver, max = 24.1)) { c.methodPointersCount = s.readUIntPtr(); c.methodPointers = s.readUIntPtr() }
            if (v(ver, max = 21.0)) { c.delegateWrappersFromNativeToManagedCount = s.readUIntPtr(); c.delegateWrappersFromNativeToManaged = s.readUIntPtr() }
            if (v(ver, 22.0)) { c.reversePInvokeWrapperCount = s.readUIntPtr(); c.reversePInvokeWrappers = s.readUIntPtr() }
            if (v(ver, max = 22.0)) {
                c.delegateWrappersFromManagedToNativeCount = s.readUIntPtr(); c.delegateWrappersFromManagedToNative = s.readUIntPtr()
                c.marshalingFunctionsCount = s.readUIntPtr(); c.marshalingFunctions = s.readUIntPtr()
            }
            if (v(ver, 21.0, 22.0)) { c.ccwMarshalingFunctionsCount = s.readUIntPtr(); c.ccwMarshalingFunctions = s.readUIntPtr() }
            c.genericMethodPointersCount = s.readUIntPtr()
            c.genericMethodPointers = s.readUIntPtr()
            if (v(ver, 24.5, 24.5) || v(ver, 27.1)) c.genericAdjustorThunks = s.readUIntPtr()
            c.invokerPointersCount = s.readUIntPtr()
            c.invokerPointers = s.readUIntPtr()
            if (v(ver, max = 24.5)) { c.customAttributeCount = s.readUIntPtr(); c.customAttributeGenerators = s.readUIntPtr() }
            if (v(ver, 21.0, 22.0)) { c.guidCount = s.readUIntPtr(); c.guids = s.readUIntPtr() }
            if (v(ver, 22.0)) { c.unresolvedVirtualCallCount = s.readUIntPtr(); c.unresolvedVirtualCallPointers = s.readUIntPtr() }
            if (v(ver, 29.1)) { c.unresolvedInstanceCallPointers = s.readUIntPtr(); c.unresolvedStaticCallPointers = s.readUIntPtr() }
            if (v(ver, 23.0)) { c.interopDataCount = s.readUIntPtr(); c.interopData = s.readUIntPtr() }
            if (v(ver, 24.3)) { c.windowsRuntimeFactoryCount = s.readUIntPtr(); c.windowsRuntimeFactoryTable = s.readUIntPtr() }
            if (v(ver, 24.2)) { c.codeGenModulesCount = s.readUIntPtr(); c.codeGenModules = s.readUIntPtr() }
            return c
        }
    }
}

class Il2CppMetadataRegistration {
    var genericClassesCount = 0L
    var genericClasses = 0L
    var genericInstsCount = 0L
    var genericInsts = 0L
    var genericMethodTableCount = 0L
    var genericMethodTable = 0L
    var typesCount = 0L
    var types = 0L
    var methodSpecsCount = 0L
    var methodSpecs = 0L
    var methodReferencesCount = 0L
    var methodReferences = 0L
    var fieldOffsetsCount = 0L
    var fieldOffsets = 0L
    var typeDefinitionsSizesCount = 0L
    var typeDefinitionsSizes = 0L
    var metadataUsagesCount = 0L
    var metadataUsages = 0L

    companion object {
        fun read(s: BinaryStream): Il2CppMetadataRegistration {
            val ver = s.version
            val m = Il2CppMetadataRegistration()
            m.genericClassesCount = s.readIntPtr(); m.genericClasses = s.readUIntPtr()
            m.genericInstsCount = s.readIntPtr(); m.genericInsts = s.readUIntPtr()
            m.genericMethodTableCount = s.readIntPtr(); m.genericMethodTable = s.readUIntPtr()
            m.typesCount = s.readIntPtr(); m.types = s.readUIntPtr()
            m.methodSpecsCount = s.readIntPtr(); m.methodSpecs = s.readUIntPtr()
            if (v(ver, max = 16.0)) { m.methodReferencesCount = s.readIntPtr(); m.methodReferences = s.readUIntPtr() }
            m.fieldOffsetsCount = s.readIntPtr(); m.fieldOffsets = s.readUIntPtr()
            m.typeDefinitionsSizesCount = s.readIntPtr(); m.typeDefinitionsSizes = s.readUIntPtr()
            if (v(ver, 19.0)) { m.metadataUsagesCount = s.readUIntPtr(); m.metadataUsages = s.readUIntPtr() }
            return m
        }
    }
}

object Il2CppTypeEnum {
    const val END = 0x00
    const val VOID = 0x01
    const val BOOLEAN = 0x02
    const val CHAR = 0x03
    const val I1 = 0x04
    const val U1 = 0x05
    const val I2 = 0x06
    const val U2 = 0x07
    const val I4 = 0x08
    const val U4 = 0x09
    const val I8 = 0x0a
    const val U8 = 0x0b
    const val R4 = 0x0c
    const val R8 = 0x0d
    const val STRING = 0x0e
    const val PTR = 0x0f
    const val BYREF = 0x10
    const val VALUETYPE = 0x11
    const val CLASS = 0x12
    const val VAR = 0x13
    const val ARRAY = 0x14
    const val GENERICINST = 0x15
    const val TYPEDBYREF = 0x16
    const val I = 0x18
    const val U = 0x19
    const val FNPTR = 0x1b
    const val OBJECT = 0x1c
    const val SZARRAY = 0x1d
    const val MVAR = 0x1e
    const val CMOD_REQD = 0x1f
    const val CMOD_OPT = 0x20
    const val INTERNAL = 0x21
    const val MODIFIER = 0x40
    const val SENTINEL = 0x41
    const val PINNED = 0x45
    const val ENUM = 0x55
    const val IL2CPP_TYPE_INDEX = 0xff
}

class Il2CppType {
    var datapoint = 0L
    var bits = 0L
    var attrs = 0
    var type = 0
    var numMods = 0
    var byref = 0
    var pinned = 0
    var valuetype = 0

    /** Union accessors. */
    val klassIndex: Long get() = datapoint
    val typeHandle: Long get() = datapoint
    val dataType: Long get() = datapoint
    val array: Long get() = datapoint
    val genericParameterIndex: Long get() = datapoint
    val genericParameterHandle: Long get() = datapoint
    val genericClass: Long get() = datapoint

    fun init(version: Double) {
        attrs = (bits and 0xffff).toInt()
        type = ((bits shr 16) and 0xff).toInt()
        if (version >= 27.2) {
            numMods = ((bits shr 24) and 0x1f).toInt()
            byref = ((bits shr 29) and 1).toInt()
            pinned = ((bits shr 30) and 1).toInt()
            valuetype = (bits shr 31).toInt()
        } else {
            numMods = ((bits shr 24) and 0x3f).toInt()
            byref = ((bits shr 30) and 1).toInt()
            pinned = (bits shr 31).toInt()
        }
    }

    override fun toString(): String = "Il2CppDumper.Il2CppType"

    companion object {
        fun read(s: BinaryStream): Il2CppType {
            val t = Il2CppType()
            t.datapoint = s.readUIntPtr()
            t.bits = s.readUInt32()
            return t
        }
    }
}

class Il2CppGenericContext(var classInst: Long = 0L, var methodInst: Long = 0L)

class Il2CppGenericClass {
    var typeDefinitionIndex = 0L
    var type = 0L
    lateinit var context: Il2CppGenericContext
    var cachedClass = 0L

    companion object {
        fun read(s: BinaryStream): Il2CppGenericClass {
            val ver = s.version
            val g = Il2CppGenericClass()
            if (v(ver, max = 24.5)) g.typeDefinitionIndex = s.readIntPtr()
            if (v(ver, 27.0)) g.type = s.readUIntPtr()
            g.context = Il2CppGenericContext(s.readUIntPtr(), s.readUIntPtr())
            g.cachedClass = s.readUIntPtr()
            return g
        }
    }
}

class Il2CppGenericInst(val typeArgc: Long, val typeArgv: Long) {
    companion object {
        fun read(s: BinaryStream) = Il2CppGenericInst(s.readIntPtr(), s.readUIntPtr())
    }
}

class Il2CppArrayType(
    val etype: Long,
    val rank: Int,
    val numsizes: Int,
    val numlobounds: Int,
    val sizes: Long,
    val lobounds: Long,
) {
    companion object {
        fun read(s: BinaryStream) = Il2CppArrayType(
            s.readUIntPtr(), s.readByte(), s.readByte(), s.readByte(), s.readUIntPtr(), s.readUIntPtr()
        )
    }
}

class Il2CppGenericMethodIndices(val methodIndex: Int, val invokerIndex: Int, val adjustorThunk: Int)

class Il2CppGenericMethodFunctionsDefinitions(val genericMethodIndex: Int, val indices: Il2CppGenericMethodIndices) {
    companion object {
        fun read(s: BinaryStream): Il2CppGenericMethodFunctionsDefinitions {
            val ver = s.version
            val gmi = s.readInt32()
            val mi = s.readInt32()
            val ii = s.readInt32()
            val at = if (v(ver, 24.5, 24.5) || v(ver, 27.1)) s.readInt32() else 0
            return Il2CppGenericMethodFunctionsDefinitions(gmi, Il2CppGenericMethodIndices(mi, ii, at))
        }
    }
}

/** Il2CppMethodSpec; identity semantics (used as dictionary key in the original). */
class Il2CppMethodSpec(val methodDefinitionIndex: Int, val classIndexIndex: Int, val methodIndexIndex: Int) {
    companion object {
        fun read(s: BinaryStream) = Il2CppMethodSpec(s.readInt32(), s.readInt32(), s.readInt32())
    }
}

class Il2CppCodeGenModule {
    var moduleName = 0L
    var methodPointerCount = 0L
    var methodPointers = 0L
    var adjustorThunkCount = 0L
    var adjustorThunks = 0L
    var invokerIndices = 0L
    var reversePInvokeWrapperCount = 0L
    var reversePInvokeWrapperIndices = 0L
    var rgctxRangesCount = 0L
    var rgctxRanges = 0L
    var rgctxsCount = 0L
    var rgctxs = 0L
    var debuggerMetadata = 0L
    var customAttributeCacheGenerator = 0L
    var moduleInitializer = 0L
    var staticConstructorTypeIndices = 0L
    var metadataRegistration = 0L
    var codeRegistaration = 0L

    companion object {
        fun read(s: BinaryStream): Il2CppCodeGenModule {
            val ver = s.version
            val m = Il2CppCodeGenModule()
            m.moduleName = s.readUIntPtr()
            m.methodPointerCount = s.readIntPtr()
            m.methodPointers = s.readUIntPtr()
            if (v(ver, 24.5, 24.5) || v(ver, 27.1)) {
                m.adjustorThunkCount = s.readIntPtr()
                m.adjustorThunks = s.readUIntPtr()
            }
            m.invokerIndices = s.readUIntPtr()
            m.reversePInvokeWrapperCount = s.readUIntPtr()
            m.reversePInvokeWrapperIndices = s.readUIntPtr()
            m.rgctxRangesCount = s.readIntPtr()
            m.rgctxRanges = s.readUIntPtr()
            m.rgctxsCount = s.readIntPtr()
            m.rgctxs = s.readUIntPtr()
            m.debuggerMetadata = s.readUIntPtr()
            if (v(ver, 27.0, 27.2)) m.customAttributeCacheGenerator = s.readUIntPtr()
            if (v(ver, 27.0)) {
                m.moduleInitializer = s.readUIntPtr()
                m.staticConstructorTypeIndices = s.readUIntPtr()
                m.metadataRegistration = s.readUIntPtr()
                m.codeRegistaration = s.readUIntPtr()
            }
            return m
        }
    }
}

class Il2CppTokenRangePair(val token: Long, val start: Int, val length: Int) {
    companion object {
        fun read(s: BinaryStream) = Il2CppTokenRangePair(s.readUInt32(), s.readInt32(), s.readInt32())
    }
}
