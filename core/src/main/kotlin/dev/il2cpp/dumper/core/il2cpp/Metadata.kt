package dev.il2cpp.dumper.core.il2cpp

import dev.il2cpp.dumper.core.io.BinaryStream
import dev.il2cpp.dumper.core.io.Utf8
import java.nio.ByteBuffer
import java.util.TreeMap

class InvalidMetadataException(message: String) : Exception(message)

/** Port of Metadata.cs */
class Metadata(buffer: ByteBuffer) : BinaryStream(buffer) {
    val header: Il2CppGlobalMetadataHeader
    var imageDefs: List<Il2CppImageDefinition>
    val assemblyDefs: List<Il2CppAssemblyDefinition>
    val typeDefs: List<Il2CppTypeDefinition>
    val methodDefs: List<Il2CppMethodDefinition>
    val parameterDefs: List<Il2CppParameterDefinition>
    val fieldDefs: List<Il2CppFieldDefinition>
    private val fieldDefaultValuesDic: HashMap<Int, Il2CppFieldDefaultValue>
    private val parameterDefaultValuesDic: HashMap<Int, Il2CppParameterDefaultValue>
    val propertyDefs: List<Il2CppPropertyDefinition>
    var attributeTypeRanges: List<Il2CppCustomAttributeTypeRange> = emptyList()
    var attributeDataRanges: List<Il2CppCustomAttributeDataRange> = emptyList()
    private var attributeTypeRangesDic: HashMap<Il2CppImageDefinition, HashMap<Long, Int>>? = null
    val stringLiterals: List<Il2CppStringLiteral>
    private var metadataUsageLists: List<Il2CppMetadataUsageList> = emptyList()
    private var metadataUsagePairs: List<Il2CppMetadataUsagePair> = emptyList()
    var attributeTypes: IntArray = IntArray(0)
    val interfaceIndices: IntArray
    var metadataUsageDic: HashMap<Int, TreeMap<Long, Long>>? = null
    var metadataUsagesCount: Long = 0
    val nestedTypeIndices: IntArray
    val eventDefs: List<Il2CppEventDefinition>
    val genericContainers: List<Il2CppGenericContainer>
    var fieldRefs: List<Il2CppFieldRef> = emptyList()
    val genericParameters: List<Il2CppGenericParameter>
    val constraintIndices: IntArray
    val vtableMethods: LongArray
    var rgctxEntries: List<Il2CppRGCTXDefinition> = emptyList()

    private val stringCache = HashMap<Long, String>()

    init {
        position = 0
        val sanity = readUInt32()
        if (sanity != 0xFAB11BAFL) {
            throw InvalidMetadataException("ERROR: Metadata file supplied is not valid metadata file.")
        }
        val ver = readInt32()
        if (ver < 0 || ver > 1000) {
            throw InvalidMetadataException("ERROR: Metadata file supplied is not valid metadata file.")
        }
        if (ver < 16 || ver > 31) {
            throw InvalidMetadataException("ERROR: Metadata file supplied is not a supported version[$ver].")
        }
        version = ver.toDouble()
        position = 0
        var hdr = Il2CppGlobalMetadataHeader.read(this)
        if (ver == 24) {
            if (hdr.stringLiteralOffset == 264L) {
                version = 24.2
                position = 0
                hdr = Il2CppGlobalMetadataHeader.read(this)
            } else {
                imageDefs = readMetadataClassArray(hdr.imagesOffset, hdr.imagesSize, Il2CppImageDefinition.size(version)) { Il2CppImageDefinition.read(this) }
                if (imageDefs.any { it.token != 1L }) {
                    version = 24.1
                }
            }
        }
        header = hdr
        imageDefs = readMetadataClassArray(header.imagesOffset, header.imagesSize, Il2CppImageDefinition.size(version)) { Il2CppImageDefinition.read(this) }
        if (version == 24.2 && header.assembliesSize / 68 < imageDefs.size) {
            version = 24.4
        }
        var v241Plus = false
        if (version == 24.1 && header.assembliesSize / 64 == imageDefs.size) {
            v241Plus = true
        }
        if (v241Plus) {
            version = 24.4
        }
        assemblyDefs = readMetadataClassArray(header.assembliesOffset, header.assembliesSize, Il2CppAssemblyDefinition.size(version)) { Il2CppAssemblyDefinition.read(this) }
        if (v241Plus) {
            version = 24.1
        }
        typeDefs = readMetadataClassArray(header.typeDefinitionsOffset, header.typeDefinitionsSize, Il2CppTypeDefinition.size(version)) { Il2CppTypeDefinition.read(this) }
        methodDefs = readMetadataClassArray(header.methodsOffset, header.methodsSize, Il2CppMethodDefinition.size(version)) { Il2CppMethodDefinition.read(this) }
        parameterDefs = readMetadataClassArray(header.parametersOffset, header.parametersSize, Il2CppParameterDefinition.size(version)) { Il2CppParameterDefinition.read(this) }
        fieldDefs = readMetadataClassArray(header.fieldsOffset, header.fieldsSize, Il2CppFieldDefinition.size(version)) { Il2CppFieldDefinition.read(this) }
        val fieldDefaultValues = readMetadataClassArray(header.fieldDefaultValuesOffset, header.fieldDefaultValuesSize, Il2CppFieldDefaultValue.SIZE) { Il2CppFieldDefaultValue.read(this) }
        val parameterDefaultValues = readMetadataClassArray(header.parameterDefaultValuesOffset, header.parameterDefaultValuesSize, Il2CppParameterDefaultValue.SIZE) { Il2CppParameterDefaultValue.read(this) }
        fieldDefaultValuesDic = HashMap(fieldDefaultValues.size * 2)
        for (x in fieldDefaultValues) fieldDefaultValuesDic.putIfAbsent(x.fieldIndex, x)
        parameterDefaultValuesDic = HashMap(parameterDefaultValues.size * 2)
        for (x in parameterDefaultValues) parameterDefaultValuesDic.putIfAbsent(x.parameterIndex, x)
        propertyDefs = readMetadataClassArray(header.propertiesOffset, header.propertiesSize, Il2CppPropertyDefinition.size(version)) { Il2CppPropertyDefinition.read(this) }
        interfaceIndices = readInt32Array(header.interfacesOffset, (header.interfacesSize / 4).toLong())
        nestedTypeIndices = readInt32Array(header.nestedTypesOffset, (header.nestedTypesSize / 4).toLong())
        eventDefs = readMetadataClassArray(header.eventsOffset, header.eventsSize, Il2CppEventDefinition.size(version)) { Il2CppEventDefinition.read(this) }
        genericContainers = readMetadataClassArray(header.genericContainersOffset, header.genericContainersSize, Il2CppGenericContainer.SIZE) { Il2CppGenericContainer.read(this) }
        genericParameters = readMetadataClassArray(header.genericParametersOffset, header.genericParametersSize, Il2CppGenericParameter.SIZE) { Il2CppGenericParameter.read(this) }
        constraintIndices = readInt32Array(header.genericParameterConstraintsOffset, (header.genericParameterConstraintsSize / 4).toLong())
        vtableMethods = readUInt32Array(header.vtableMethodsOffset, (header.vtableMethodsSize / 4).toLong())
        stringLiterals = readMetadataClassArray(header.stringLiteralOffset, header.stringLiteralSize, Il2CppStringLiteral.SIZE) { Il2CppStringLiteral.read(this) }
        if (version > 16) {
            fieldRefs = readMetadataClassArray(header.fieldRefsOffset, header.fieldRefsSize, Il2CppFieldRef.SIZE) { Il2CppFieldRef.read(this) }
            if (version < 27) {
                metadataUsageLists = readMetadataClassArray(header.metadataUsageListsOffset, header.metadataUsageListsCount, Il2CppMetadataUsageList.SIZE) { Il2CppMetadataUsageList.read(this) }
                metadataUsagePairs = readMetadataClassArray(header.metadataUsagePairsOffset, header.metadataUsagePairsCount, Il2CppMetadataUsagePair.SIZE) { Il2CppMetadataUsagePair.read(this) }
                processingMetadataUsage()
            }
        }
        if (version > 20 && version < 29) {
            attributeTypeRanges = readMetadataClassArray(header.attributesInfoOffset, header.attributesInfoCount, Il2CppCustomAttributeTypeRange.size(version)) { Il2CppCustomAttributeTypeRange.read(this) }
            attributeTypes = readInt32Array(header.attributeTypesOffset, (header.attributeTypesCount / 4).toLong())
        }
        if (version >= 29) {
            attributeDataRanges = readMetadataClassArray(header.attributeDataRangeOffset, header.attributeDataRangeSize, Il2CppCustomAttributeDataRange.SIZE) { Il2CppCustomAttributeDataRange.read(this) }
        }
        if (version > 24) {
            val dicAll = HashMap<Il2CppImageDefinition, HashMap<Long, Int>>()
            for (imageDef in imageDefs) {
                val dic = HashMap<Long, Int>()
                dicAll[imageDef] = dic
                val end = imageDef.customAttributeStart + imageDef.customAttributeCount
                var i = imageDef.customAttributeStart
                while (i < end) {
                    if (version >= 29) {
                        dic.putIfAbsent(attributeDataRanges[i].token, i)
                    } else {
                        dic.putIfAbsent(attributeTypeRanges[i].token, i)
                    }
                    i++
                }
            }
            attributeTypeRangesDic = dicAll
        }
        if (version <= 24.1) {
            rgctxEntries = readMetadataClassArray(header.rgctxEntriesOffset, header.rgctxEntriesCount, Il2CppRGCTXDefinition.metadataSize(version)) { Il2CppRGCTXDefinition.read(this) }
        }
    }

    private inline fun <T> readMetadataClassArray(addr: Long, count: Int, size: Int, reader: () -> T): List<T> {
        if (size == 0) throw ArithmeticException("Attempted to divide by zero.")
        return readArray(addr, (count / size).toLong(), size, reader)
    }

    fun getFieldDefaultValueFromIndex(index: Int): Il2CppFieldDefaultValue? = fieldDefaultValuesDic[index]

    fun getParameterDefaultValueFromIndex(index: Int): Il2CppParameterDefaultValue? = parameterDefaultValuesDic[index]

    fun getDefaultValueFromIndex(index: Int): Long = (header.fieldAndParameterDefaultValueDataOffset + index) and 0xFFFFFFFFL

    fun getStringFromIndex(index: Long): String {
        return stringCache.getOrPut(index) { readStringToNull(header.stringOffset + index) }
    }

    fun getCustomAttributeIndex(imageDef: Il2CppImageDefinition, customAttributeIndex: Int, token: Long): Int {
        return if (version > 24) {
            attributeTypeRangesDic!![imageDef]?.get(token) ?: -1
        } else {
            customAttributeIndex
        }
    }

    fun getStringLiteralFromIndex(index: Long): String {
        val stringLiteral = stringLiterals[index.toInt()]
        position = (header.stringLiteralDataOffset + stringLiteral.dataIndex) and 0xFFFFFFFFL
        return Utf8.decode(readBytes(stringLiteral.length.toInt()))
    }

    private fun processingMetadataUsage() {
        val dic = HashMap<Int, TreeMap<Long, Long>>()
        for (i in 1..6) dic[i] = TreeMap()
        for (metadataUsageList in metadataUsageLists) {
            var i = 0L
            while (i < metadataUsageList.count) {
                val offset = metadataUsageList.start + i
                i++
                if (offset >= metadataUsagePairs.size) continue
                val metadataUsagePair = metadataUsagePairs[offset.toInt()]
                val usage = getEncodedIndexType(metadataUsagePair.encodedSourceIndex)
                val decodedIndex = getDecodedMethodIndex(metadataUsagePair.encodedSourceIndex)
                dic[usage.toInt()]!![metadataUsagePair.destinationIndex] = decodedIndex
            }
        }
        metadataUsageDic = dic
        metadataUsagesCount = dic.values.maxOf { m -> if (m.isEmpty()) 0L else m.lastKey() } + 1
    }

    fun getDecodedMethodIndex(index: Long): Long {
        if (version >= 27) {
            return (index and 0x1FFFFFFEL) shr 1
        }
        return index and 0x1FFFFFFFL
    }

    fun sizeOfTypeDefinition(): Int = Il2CppTypeDefinition.size(version)

    fun sizeOfGenericParameter(): Int = Il2CppGenericParameter.SIZE

    companion object {
        const val SANITY = 0xFAB11BAFL

        fun getEncodedIndexType(index: Long): Long = (index and 0xE0000000L) shr 29
    }
}
