package dev.il2cpp.dumper.core.il2cpp

import dev.il2cpp.dumper.core.io.BinaryStream

/*
 * Port of MetadataClass.cs. Every structure has an explicit reader that follows the
 * [Version(Min, Max)] attributes of the original, and a size() that mirrors Metadata.SizeOf
 * (which only counts 2/4 byte primitives, byte arrays and nested classes).
 */

internal fun v(version: Double, min: Double = 0.0, max: Double = 99.0) = version >= min && version <= max

class Il2CppGlobalMetadataHeader {
    var sanity = 0L
    var version = 0
    var stringLiteralOffset = 0L; var stringLiteralSize = 0
    var stringLiteralDataOffset = 0L; var stringLiteralDataSize = 0
    var stringOffset = 0L; var stringSize = 0
    var eventsOffset = 0L; var eventsSize = 0
    var propertiesOffset = 0L; var propertiesSize = 0
    var methodsOffset = 0L; var methodsSize = 0
    var parameterDefaultValuesOffset = 0L; var parameterDefaultValuesSize = 0
    var fieldDefaultValuesOffset = 0L; var fieldDefaultValuesSize = 0
    var fieldAndParameterDefaultValueDataOffset = 0L; var fieldAndParameterDefaultValueDataSize = 0
    var fieldMarshaledSizesOffset = 0; var fieldMarshaledSizesSize = 0
    var parametersOffset = 0L; var parametersSize = 0
    var fieldsOffset = 0L; var fieldsSize = 0
    var genericParametersOffset = 0L; var genericParametersSize = 0
    var genericParameterConstraintsOffset = 0L; var genericParameterConstraintsSize = 0
    var genericContainersOffset = 0L; var genericContainersSize = 0
    var nestedTypesOffset = 0L; var nestedTypesSize = 0
    var interfacesOffset = 0L; var interfacesSize = 0
    var vtableMethodsOffset = 0L; var vtableMethodsSize = 0
    var interfaceOffsetsOffset = 0; var interfaceOffsetsSize = 0
    var typeDefinitionsOffset = 0L; var typeDefinitionsSize = 0
    var rgctxEntriesOffset = 0L; var rgctxEntriesCount = 0
    var imagesOffset = 0L; var imagesSize = 0
    var assembliesOffset = 0L; var assembliesSize = 0
    var metadataUsageListsOffset = 0L; var metadataUsageListsCount = 0
    var metadataUsagePairsOffset = 0L; var metadataUsagePairsCount = 0
    var fieldRefsOffset = 0L; var fieldRefsSize = 0
    var referencedAssembliesOffset = 0; var referencedAssembliesSize = 0
    var attributesInfoOffset = 0L; var attributesInfoCount = 0
    var attributeTypesOffset = 0L; var attributeTypesCount = 0
    var attributeDataOffset = 0L; var attributeDataSize = 0
    var attributeDataRangeOffset = 0L; var attributeDataRangeSize = 0
    var unresolvedVirtualCallParameterTypesOffset = 0; var unresolvedVirtualCallParameterTypesSize = 0
    var unresolvedVirtualCallParameterRangesOffset = 0; var unresolvedVirtualCallParameterRangesSize = 0
    var windowsRuntimeTypeNamesOffset = 0; var windowsRuntimeTypeNamesSize = 0
    var windowsRuntimeStringsOffset = 0; var windowsRuntimeStringsSize = 0
    var exportedTypeDefinitionsOffset = 0; var exportedTypeDefinitionsSize = 0

    companion object {
        fun read(s: BinaryStream): Il2CppGlobalMetadataHeader {
            val ver = s.version
            val h = Il2CppGlobalMetadataHeader()
            h.sanity = s.readUInt32()
            h.version = s.readInt32()
            h.stringLiteralOffset = s.readUInt32(); h.stringLiteralSize = s.readInt32()
            h.stringLiteralDataOffset = s.readUInt32(); h.stringLiteralDataSize = s.readInt32()
            h.stringOffset = s.readUInt32(); h.stringSize = s.readInt32()
            h.eventsOffset = s.readUInt32(); h.eventsSize = s.readInt32()
            h.propertiesOffset = s.readUInt32(); h.propertiesSize = s.readInt32()
            h.methodsOffset = s.readUInt32(); h.methodsSize = s.readInt32()
            h.parameterDefaultValuesOffset = s.readUInt32(); h.parameterDefaultValuesSize = s.readInt32()
            h.fieldDefaultValuesOffset = s.readUInt32(); h.fieldDefaultValuesSize = s.readInt32()
            h.fieldAndParameterDefaultValueDataOffset = s.readUInt32(); h.fieldAndParameterDefaultValueDataSize = s.readInt32()
            h.fieldMarshaledSizesOffset = s.readInt32(); h.fieldMarshaledSizesSize = s.readInt32()
            h.parametersOffset = s.readUInt32(); h.parametersSize = s.readInt32()
            h.fieldsOffset = s.readUInt32(); h.fieldsSize = s.readInt32()
            h.genericParametersOffset = s.readUInt32(); h.genericParametersSize = s.readInt32()
            h.genericParameterConstraintsOffset = s.readUInt32(); h.genericParameterConstraintsSize = s.readInt32()
            h.genericContainersOffset = s.readUInt32(); h.genericContainersSize = s.readInt32()
            h.nestedTypesOffset = s.readUInt32(); h.nestedTypesSize = s.readInt32()
            h.interfacesOffset = s.readUInt32(); h.interfacesSize = s.readInt32()
            h.vtableMethodsOffset = s.readUInt32(); h.vtableMethodsSize = s.readInt32()
            h.interfaceOffsetsOffset = s.readInt32(); h.interfaceOffsetsSize = s.readInt32()
            h.typeDefinitionsOffset = s.readUInt32(); h.typeDefinitionsSize = s.readInt32()
            if (v(ver, max = 24.1)) { h.rgctxEntriesOffset = s.readUInt32(); h.rgctxEntriesCount = s.readInt32() }
            h.imagesOffset = s.readUInt32(); h.imagesSize = s.readInt32()
            h.assembliesOffset = s.readUInt32(); h.assembliesSize = s.readInt32()
            if (v(ver, 19.0, 24.5)) {
                h.metadataUsageListsOffset = s.readUInt32(); h.metadataUsageListsCount = s.readInt32()
                h.metadataUsagePairsOffset = s.readUInt32(); h.metadataUsagePairsCount = s.readInt32()
            }
            if (v(ver, 19.0)) { h.fieldRefsOffset = s.readUInt32(); h.fieldRefsSize = s.readInt32() }
            if (v(ver, 20.0)) { h.referencedAssembliesOffset = s.readInt32(); h.referencedAssembliesSize = s.readInt32() }
            if (v(ver, 21.0, 27.2)) {
                h.attributesInfoOffset = s.readUInt32(); h.attributesInfoCount = s.readInt32()
                h.attributeTypesOffset = s.readUInt32(); h.attributeTypesCount = s.readInt32()
            }
            if (v(ver, 29.0)) {
                h.attributeDataOffset = s.readUInt32(); h.attributeDataSize = s.readInt32()
                h.attributeDataRangeOffset = s.readUInt32(); h.attributeDataRangeSize = s.readInt32()
            }
            if (v(ver, 22.0)) {
                h.unresolvedVirtualCallParameterTypesOffset = s.readInt32(); h.unresolvedVirtualCallParameterTypesSize = s.readInt32()
                h.unresolvedVirtualCallParameterRangesOffset = s.readInt32(); h.unresolvedVirtualCallParameterRangesSize = s.readInt32()
            }
            if (v(ver, 23.0)) { h.windowsRuntimeTypeNamesOffset = s.readInt32(); h.windowsRuntimeTypeNamesSize = s.readInt32() }
            if (v(ver, 27.0)) { h.windowsRuntimeStringsOffset = s.readInt32(); h.windowsRuntimeStringsSize = s.readInt32() }
            if (v(ver, 24.0)) { h.exportedTypeDefinitionsOffset = s.readInt32(); h.exportedTypeDefinitionsSize = s.readInt32() }
            return h
        }

        /** Size of the header for a version (used by tools that rebuild damaged headers). */
        fun size(ver: Double): Int {
            var pairs = 20
            if (v(ver, max = 24.1)) pairs++
            pairs += 2
            if (v(ver, 19.0, 24.5)) pairs += 2
            if (v(ver, 19.0)) pairs++
            if (v(ver, 20.0)) pairs++
            if (v(ver, 21.0, 27.2)) pairs += 2
            if (v(ver, 29.0)) pairs += 2
            if (v(ver, 22.0)) pairs += 2
            if (v(ver, 23.0)) pairs++
            if (v(ver, 27.0)) pairs++
            if (v(ver, 24.0)) pairs++
            return 8 + pairs * 8
        }
    }
}

class Il2CppAssemblyNameDefinition {
    var nameIndex = 0L
    var cultureIndex = 0L
    var hashValueIndex = 0
    var publicKeyIndex = 0L
    var hashAlg = 0L
    var hashLen = 0
    var flags = 0L
    var major = 0
    var minor = 0
    var build = 0
    var revision = 0
    var publicKeyToken = ByteArray(8)

    companion object {
        fun read(s: BinaryStream): Il2CppAssemblyNameDefinition {
            val ver = s.version
            val a = Il2CppAssemblyNameDefinition()
            a.nameIndex = s.readUInt32()
            a.cultureIndex = s.readUInt32()
            if (v(ver, max = 24.3)) a.hashValueIndex = s.readInt32()
            a.publicKeyIndex = s.readUInt32()
            a.hashAlg = s.readUInt32()
            a.hashLen = s.readInt32()
            a.flags = s.readUInt32()
            a.major = s.readInt32()
            a.minor = s.readInt32()
            a.build = s.readInt32()
            a.revision = s.readInt32()
            a.publicKeyToken = ByteArray(8) { s.readByte().toByte() }
            return a
        }

        fun size(ver: Double) = 40 + (if (v(ver, max = 24.3)) 4 else 0) + 8
    }
}

class Il2CppAssemblyDefinition {
    var imageIndex = 0
    var token = 0L
    var customAttributeIndex = 0
    var referencedAssemblyStart = 0
    var referencedAssemblyCount = 0
    lateinit var aname: Il2CppAssemblyNameDefinition

    companion object {
        fun read(s: BinaryStream): Il2CppAssemblyDefinition {
            val ver = s.version
            val a = Il2CppAssemblyDefinition()
            a.imageIndex = s.readInt32()
            if (v(ver, 24.1)) a.token = s.readUInt32()
            if (v(ver, max = 24.0)) a.customAttributeIndex = s.readInt32()
            if (v(ver, 20.0)) { a.referencedAssemblyStart = s.readInt32(); a.referencedAssemblyCount = s.readInt32() }
            a.aname = Il2CppAssemblyNameDefinition.read(s)
            return a
        }

        fun size(ver: Double): Int {
            var size = 4
            if (v(ver, 24.1)) size += 4
            if (v(ver, max = 24.0)) size += 4
            if (v(ver, 20.0)) size += 8
            return size + Il2CppAssemblyNameDefinition.size(ver)
        }
    }
}

class Il2CppImageDefinition {
    var nameIndex = 0L
    var assemblyIndex = 0
    var typeStart = 0
    var typeCount = 0L
    var exportedTypeStart = 0
    var exportedTypeCount = 0L
    var entryPointIndex = 0
    var token = 0L
    var customAttributeStart = 0
    var customAttributeCount = 0L

    companion object {
        fun read(s: BinaryStream): Il2CppImageDefinition {
            val ver = s.version
            val d = Il2CppImageDefinition()
            d.nameIndex = s.readUInt32()
            d.assemblyIndex = s.readInt32()
            d.typeStart = s.readInt32()
            d.typeCount = s.readUInt32()
            if (v(ver, 24.0)) { d.exportedTypeStart = s.readInt32(); d.exportedTypeCount = s.readUInt32() }
            d.entryPointIndex = s.readInt32()
            if (v(ver, 19.0)) d.token = s.readUInt32()
            if (v(ver, 24.1)) { d.customAttributeStart = s.readInt32(); d.customAttributeCount = s.readUInt32() }
            return d
        }

        fun size(ver: Double): Int {
            var size = 16
            if (v(ver, 24.0)) size += 8
            size += 4
            if (v(ver, 19.0)) size += 4
            if (v(ver, 24.1)) size += 8
            return size
        }
    }
}

class Il2CppTypeDefinition {
    var nameIndex = 0L
    var namespaceIndex = 0L
    var customAttributeIndex = 0
    var byvalTypeIndex = 0
    var byrefTypeIndex = 0
    var declaringTypeIndex = 0
    var parentIndex = 0
    var elementTypeIndex = 0
    var rgctxStartIndex = 0
    var rgctxCount = 0
    var genericContainerIndex = 0
    var delegateWrapperFromManagedToNativeIndex = 0
    var marshalingFunctionsIndex = 0
    var ccwFunctionIndex = 0
    var guidIndex = 0
    var flags = 0L
    var fieldStart = 0
    var methodStart = 0
    var eventStart = 0
    var propertyStart = 0
    var nestedTypesStart = 0
    var interfacesStart = 0
    var vtableStart = 0
    var interfaceOffsetsStart = 0
    var methodCount = 0
    var propertyCount = 0
    var fieldCount = 0
    var eventCount = 0
    var nestedTypeCount = 0
    var vtableCount = 0
    var interfacesCount = 0
    var interfaceOffsetsCount = 0
    var bitfield = 0L
    var token = 0L

    val isValueType: Boolean get() = (bitfield and 0x1L) == 1L
    val isEnum: Boolean get() = ((bitfield shr 1) and 0x1L) == 1L

    companion object {
        fun read(s: BinaryStream): Il2CppTypeDefinition {
            val ver = s.version
            val t = Il2CppTypeDefinition()
            t.nameIndex = s.readUInt32()
            t.namespaceIndex = s.readUInt32()
            if (v(ver, max = 24.0)) t.customAttributeIndex = s.readInt32()
            t.byvalTypeIndex = s.readInt32()
            if (v(ver, max = 24.5)) t.byrefTypeIndex = s.readInt32()
            t.declaringTypeIndex = s.readInt32()
            t.parentIndex = s.readInt32()
            t.elementTypeIndex = s.readInt32()
            if (v(ver, max = 24.1)) { t.rgctxStartIndex = s.readInt32(); t.rgctxCount = s.readInt32() }
            t.genericContainerIndex = s.readInt32()
            if (v(ver, max = 22.0)) { t.delegateWrapperFromManagedToNativeIndex = s.readInt32(); t.marshalingFunctionsIndex = s.readInt32() }
            if (v(ver, 21.0, 22.0)) { t.ccwFunctionIndex = s.readInt32(); t.guidIndex = s.readInt32() }
            t.flags = s.readUInt32()
            t.fieldStart = s.readInt32()
            t.methodStart = s.readInt32()
            t.eventStart = s.readInt32()
            t.propertyStart = s.readInt32()
            t.nestedTypesStart = s.readInt32()
            t.interfacesStart = s.readInt32()
            t.vtableStart = s.readInt32()
            t.interfaceOffsetsStart = s.readInt32()
            t.methodCount = s.readUInt16()
            t.propertyCount = s.readUInt16()
            t.fieldCount = s.readUInt16()
            t.eventCount = s.readUInt16()
            t.nestedTypeCount = s.readUInt16()
            t.vtableCount = s.readUInt16()
            t.interfacesCount = s.readUInt16()
            t.interfaceOffsetsCount = s.readUInt16()
            t.bitfield = s.readUInt32()
            if (v(ver, 19.0)) t.token = s.readUInt32()
            return t
        }

        fun size(ver: Double): Int {
            var size = 8
            if (v(ver, max = 24.0)) size += 4
            size += 4
            if (v(ver, max = 24.5)) size += 4
            size += 12
            if (v(ver, max = 24.1)) size += 8
            size += 4
            if (v(ver, max = 22.0)) size += 8
            if (v(ver, 21.0, 22.0)) size += 8
            size += 4 + 32 + 16 + 4
            if (v(ver, 19.0)) size += 4
            return size
        }
    }
}

class Il2CppMethodDefinition {
    var nameIndex = 0L
    var declaringType = 0
    var returnType = 0
    var returnParameterToken = 0
    var parameterStart = 0
    var customAttributeIndex = 0
    var genericContainerIndex = 0
    var methodIndex = 0
    var invokerIndex = 0
    var delegateWrapperIndex = 0
    var rgctxStartIndex = 0
    var rgctxCount = 0
    var token = 0L
    var flags = 0
    var iflags = 0
    var slot = 0
    var parameterCount = 0

    companion object {
        fun read(s: BinaryStream): Il2CppMethodDefinition {
            val ver = s.version
            val m = Il2CppMethodDefinition()
            m.nameIndex = s.readUInt32()
            m.declaringType = s.readInt32()
            m.returnType = s.readInt32()
            if (v(ver, 31.0)) m.returnParameterToken = s.readInt32()
            m.parameterStart = s.readInt32()
            if (v(ver, max = 24.0)) m.customAttributeIndex = s.readInt32()
            m.genericContainerIndex = s.readInt32()
            if (v(ver, max = 24.1)) {
                m.methodIndex = s.readInt32()
                m.invokerIndex = s.readInt32()
                m.delegateWrapperIndex = s.readInt32()
                m.rgctxStartIndex = s.readInt32()
                m.rgctxCount = s.readInt32()
            }
            m.token = s.readUInt32()
            m.flags = s.readUInt16()
            m.iflags = s.readUInt16()
            m.slot = s.readUInt16()
            m.parameterCount = s.readUInt16()
            return m
        }

        fun size(ver: Double): Int {
            var size = 12
            if (v(ver, 31.0)) size += 4
            size += 4
            if (v(ver, max = 24.0)) size += 4
            size += 4
            if (v(ver, max = 24.1)) size += 20
            return size + 4 + 8
        }
    }
}

class Il2CppParameterDefinition {
    var nameIndex = 0L
    var token = 0L
    var customAttributeIndex = 0
    var typeIndex = 0

    companion object {
        fun read(s: BinaryStream): Il2CppParameterDefinition {
            val p = Il2CppParameterDefinition()
            p.nameIndex = s.readUInt32()
            p.token = s.readUInt32()
            if (v(s.version, max = 24.0)) p.customAttributeIndex = s.readInt32()
            p.typeIndex = s.readInt32()
            return p
        }

        fun size(ver: Double) = 12 + if (v(ver, max = 24.0)) 4 else 0
    }
}

class Il2CppFieldDefinition {
    var nameIndex = 0L
    var typeIndex = 0
    var customAttributeIndex = 0
    var token = 0L

    companion object {
        fun read(s: BinaryStream): Il2CppFieldDefinition {
            val f = Il2CppFieldDefinition()
            f.nameIndex = s.readUInt32()
            f.typeIndex = s.readInt32()
            if (v(s.version, max = 24.0)) f.customAttributeIndex = s.readInt32()
            if (v(s.version, 19.0)) f.token = s.readUInt32()
            return f
        }

        fun size(ver: Double) = 8 + (if (v(ver, max = 24.0)) 4 else 0) + (if (v(ver, 19.0)) 4 else 0)
    }
}

class Il2CppFieldDefaultValue(val fieldIndex: Int, val typeIndex: Int, val dataIndex: Int) {
    companion object {
        fun read(s: BinaryStream) = Il2CppFieldDefaultValue(s.readInt32(), s.readInt32(), s.readInt32())
        const val SIZE = 12
    }
}

class Il2CppParameterDefaultValue(val parameterIndex: Int, val typeIndex: Int, val dataIndex: Int) {
    companion object {
        fun read(s: BinaryStream) = Il2CppParameterDefaultValue(s.readInt32(), s.readInt32(), s.readInt32())
        const val SIZE = 12
    }
}

class Il2CppPropertyDefinition {
    var nameIndex = 0L
    var get = 0
    var set = 0
    var attrs = 0L
    var customAttributeIndex = 0
    var token = 0L

    companion object {
        fun read(s: BinaryStream): Il2CppPropertyDefinition {
            val p = Il2CppPropertyDefinition()
            p.nameIndex = s.readUInt32()
            p.get = s.readInt32()
            p.set = s.readInt32()
            p.attrs = s.readUInt32()
            if (v(s.version, max = 24.0)) p.customAttributeIndex = s.readInt32()
            if (v(s.version, 19.0)) p.token = s.readUInt32()
            return p
        }

        fun size(ver: Double) = 16 + (if (v(ver, max = 24.0)) 4 else 0) + (if (v(ver, 19.0)) 4 else 0)
    }
}

class Il2CppCustomAttributeTypeRange(val token: Long, val start: Int, val count: Int) {
    companion object {
        fun read(s: BinaryStream): Il2CppCustomAttributeTypeRange {
            val token = if (v(s.version, 24.1)) s.readUInt32() else 0L
            return Il2CppCustomAttributeTypeRange(token, s.readInt32(), s.readInt32())
        }

        fun size(ver: Double) = 8 + if (v(ver, 24.1)) 4 else 0
    }
}

class Il2CppMetadataUsageList(val start: Long, val count: Long) {
    companion object {
        fun read(s: BinaryStream) = Il2CppMetadataUsageList(s.readUInt32(), s.readUInt32())
        const val SIZE = 8
    }
}

class Il2CppMetadataUsagePair(val destinationIndex: Long, val encodedSourceIndex: Long) {
    companion object {
        fun read(s: BinaryStream) = Il2CppMetadataUsagePair(s.readUInt32(), s.readUInt32())
        const val SIZE = 8
    }
}

class Il2CppStringLiteral(val length: Long, val dataIndex: Int) {
    companion object {
        fun read(s: BinaryStream) = Il2CppStringLiteral(s.readUInt32(), s.readInt32())
        const val SIZE = 8
    }
}

class Il2CppEventDefinition {
    var nameIndex = 0L
    var typeIndex = 0
    var add = 0
    var remove = 0
    var raise = 0
    var customAttributeIndex = 0
    var token = 0L

    companion object {
        fun read(s: BinaryStream): Il2CppEventDefinition {
            val e = Il2CppEventDefinition()
            e.nameIndex = s.readUInt32()
            e.typeIndex = s.readInt32()
            e.add = s.readInt32()
            e.remove = s.readInt32()
            e.raise = s.readInt32()
            if (v(s.version, max = 24.0)) e.customAttributeIndex = s.readInt32()
            if (v(s.version, 19.0)) e.token = s.readUInt32()
            return e
        }

        fun size(ver: Double) = 20 + (if (v(ver, max = 24.0)) 4 else 0) + (if (v(ver, 19.0)) 4 else 0)
    }
}

class Il2CppGenericContainer(val ownerIndex: Int, val typeArgc: Int, val isMethod: Int, val genericParameterStart: Int) {
    companion object {
        fun read(s: BinaryStream) = Il2CppGenericContainer(s.readInt32(), s.readInt32(), s.readInt32(), s.readInt32())
        const val SIZE = 16
    }
}

class Il2CppFieldRef(val typeIndex: Int, val fieldIndex: Int) {
    companion object {
        fun read(s: BinaryStream) = Il2CppFieldRef(s.readInt32(), s.readInt32())
        const val SIZE = 8
    }
}

class Il2CppGenericParameter(
    val ownerIndex: Int,
    val nameIndex: Long,
    val constraintsStart: Int,
    val constraintsCount: Int,
    val num: Int,
    val flags: Int,
) {
    companion object {
        fun read(s: BinaryStream) = Il2CppGenericParameter(
            s.readInt32(), s.readUInt32(), s.readInt16(), s.readInt16(), s.readUInt16(), s.readUInt16()
        )
        const val SIZE = 16
    }
}

object Il2CppRGCTXDataType {
    const val INVALID = 0
    const val TYPE = 1
    const val CLASS = 2
    const val METHOD = 3
    const val ARRAY = 4
    const val CONSTRAINED = 5
}

/** Il2CppRGCTXDefinition, readable from metadata (<= 24.1) and from the binary (>= 24.2). */
class Il2CppRGCTXDefinition {
    var typePre29 = 0
    var typePost29 = 0L
    var data = 0 // Il2CppRGCTXDefinitionData.rgctxDataDummy
    var dataPtr = 0L // _data

    val type: Int get() = if (typePost29 == 0L) typePre29 else typePost29.toInt()

    companion object {
        /** Reader for both metadata and binary streams; pointer sized fields use ReadUIntPtr like ReadClass<T>. */
        fun read(s: BinaryStream): Il2CppRGCTXDefinition {
            val ver = s.version
            val r = Il2CppRGCTXDefinition()
            if (v(ver, max = 27.1)) r.typePre29 = s.readInt32()
            if (v(ver, 29.0)) r.typePost29 = s.readUIntPtr()
            if (v(ver, max = 27.1)) r.data = s.readInt32()
            if (v(ver, 27.2)) r.dataPtr = s.readUIntPtr()
            return r
        }

        fun metadataSize(ver: Double): Int = (if (v(ver, max = 27.1)) 8 else 0)
    }
}

object Il2CppMetadataUsage {
    const val Invalid = 0
    const val TypeInfo = 1
    const val Il2CppType = 2
    const val MethodDef = 3
    const val FieldInfo = 4
    const val StringLiteral = 5
    const val MethodRef = 6
}

class Il2CppCustomAttributeDataRange(val token: Long, val startOffset: Long) {
    companion object {
        fun read(s: BinaryStream) = Il2CppCustomAttributeDataRange(s.readUInt32(), s.readUInt32())
        const val SIZE = 8
    }
}
