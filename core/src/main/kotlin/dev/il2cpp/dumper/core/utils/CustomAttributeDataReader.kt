package dev.il2cpp.dumper.core.utils

import dev.il2cpp.dumper.core.il2cpp.Il2CppType
import dev.il2cpp.dumper.core.il2cpp.Il2CppTypeDefinition
import dev.il2cpp.dumper.core.il2cpp.Il2CppTypeEnum
import dev.il2cpp.dumper.core.io.ByteArrayReader
import dev.il2cpp.dumper.core.io.DataReader

class AttributeArgument(var value: BlobValue? = null, var index: Int = 0)

class CustomAttributeReaderVisitor(
    var ctorIndex: Int = 0,
    var arguments: Array<AttributeArgument> = emptyArray(),
    var fields: Array<AttributeArgument> = emptyArray(),
    var properties: Array<AttributeArgument> = emptyArray(),
)

/** Port of CustomAttributeDataReader.cs (metadata v29+ attribute blobs). */
class CustomAttributeDataReader(private val executor: Il2CppExecutor, buff: ByteArray) {
    private val reader: DataReader = ByteArrayReader(buff)
    private val metadata = executor.metadata
    private var ctorBuffer: Long
    private var dataBuffer: Long
    val count: Long

    init {
        count = reader.readCompressedUInt32()
        ctorBuffer = reader.position
        dataBuffer = reader.position + count * 4
    }

    fun getStringCustomAttributeData(): String {
        reader.position = ctorBuffer
        val ctorIndex = reader.readInt32()
        val methodDef = metadata.methodDefs[ctorIndex]
        val typeDef = metadata.typeDefs[methodDef.declaringType]
        ctorBuffer = reader.position

        reader.position = dataBuffer
        val argumentCount = reader.readCompressedUInt32()
        val fieldCount = reader.readCompressedUInt32()
        val propertyCount = reader.readCompressedUInt32()

        val argList = ArrayList<String>()
        for (i in 0 until argumentCount) {
            argList.add(attributeDataToString(readAttributeDataValue()))
        }
        for (i in 0 until fieldCount) {
            val str = attributeDataToString(readAttributeDataValue())
            val (declaring, fieldIndex) = readCustomAttributeNamedArgumentClassAndIndex(typeDef)
            val fieldDef = metadata.fieldDefs[declaring.fieldStart + fieldIndex]
            argList.add("${metadata.getStringFromIndex(fieldDef.nameIndex)} = $str")
        }
        for (i in 0 until propertyCount) {
            val str = attributeDataToString(readAttributeDataValue())
            val (declaring, propertyIndex) = readCustomAttributeNamedArgumentClassAndIndex(typeDef)
            val propertyDef = metadata.propertyDefs[declaring.propertyStart + propertyIndex]
            argList.add("${metadata.getStringFromIndex(propertyDef.nameIndex)} = $str")
        }
        dataBuffer = reader.position

        val typeName = CSharp.replace(metadata.getStringFromIndex(typeDef.nameIndex), "Attribute", "")
        return if (argList.size > 0) "[$typeName(${argList.joinToString(", ")})]" else "[$typeName]"
    }

    private fun attributeDataToString(blobValue: BlobValue): String {
        val v = blobValue.value ?: return "null"
        return when (blobValue.il2CppTypeEnum) {
            Il2CppTypeEnum.STRING -> "\"$v\""
            Il2CppTypeEnum.SZARRAY -> {
                @Suppress("UNCHECKED_CAST")
                val array = v as Array<BlobValue?>
                "new[] { ${array.joinToString(", ") { attributeDataToString(it!!) }} }"
            }
            Il2CppTypeEnum.IL2CPP_TYPE_INDEX -> "typeof(${executor.getTypeName(v as Il2CppType, false, false)})"
            else -> CSharp.toString(v)
        }
    }

    fun visitCustomAttributeData(): CustomAttributeReaderVisitor {
        val visitor = CustomAttributeReaderVisitor()
        reader.position = ctorBuffer
        val ctorIndex = reader.readInt32()
        visitor.ctorIndex = ctorIndex
        val methodDef = metadata.methodDefs[ctorIndex]
        val typeDef = metadata.typeDefs[methodDef.declaringType]
        ctorBuffer = reader.position

        reader.position = dataBuffer
        val argumentCount = reader.readCompressedUInt32().toInt()
        val fieldCount = reader.readCompressedUInt32().toInt()
        val propertyCount = reader.readCompressedUInt32().toInt()

        visitor.arguments = Array(argumentCount) { i -> AttributeArgument(readAttributeDataValue(), i) }
        visitor.fields = Array(fieldCount) {
            val value = readAttributeDataValue()
            val (declaring, fieldIndex) = readCustomAttributeNamedArgumentClassAndIndex(typeDef)
            AttributeArgument(value, declaring.fieldStart + fieldIndex)
        }
        visitor.properties = Array(propertyCount) {
            val value = readAttributeDataValue()
            val (declaring, propertyIndex) = readCustomAttributeNamedArgumentClassAndIndex(typeDef)
            AttributeArgument(value, declaring.propertyStart + propertyIndex)
        }
        dataBuffer = reader.position
        return visitor
    }

    private fun readAttributeDataValue(): BlobValue {
        val (type, enumType) = executor.readEncodedTypeEnum(reader)
        val blobValue = executor.getConstantValueFromBlob(type, reader)
            ?: throw NullPointerException("Unsupported attribute value type $type")
        if (enumType != null) blobValue.enumType = enumType
        return blobValue
    }

    private fun readCustomAttributeNamedArgumentClassAndIndex(typeDef: Il2CppTypeDefinition): Pair<Il2CppTypeDefinition, Int> {
        var memberIndex = reader.readCompressedInt32()
        if (memberIndex >= 0) return typeDef to memberIndex
        memberIndex = -(memberIndex + 1)
        val typeIndex = reader.readCompressedUInt32()
        val declaringClass = metadata.typeDefs[typeIndex.toInt()]
        return declaringClass to memberIndex
    }
}
