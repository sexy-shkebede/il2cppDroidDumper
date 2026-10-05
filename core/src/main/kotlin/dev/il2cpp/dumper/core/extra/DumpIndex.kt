package dev.il2cpp.dumper.core.extra

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Compact, searchable model of a dump (what dump.cs contains, but structured).
 * Powers the in-app class browser, search, code generators and the dump diff tool.
 */
class DumpIndex(
    val info: DumpInfo,
    val images: List<String>,
    val types: List<TypeEntry>,
    val strings: List<StringLiteralEntry>,
) {
    val methodCount: Int get() = types.sumOf { it.methods.size }
    val fieldCount: Int get() = types.sumOf { it.fields.size }

    fun save(file: File) {
        DataOutputStream(BufferedOutputStream(GZIPOutputStream(FileOutputStream(file), 1 shl 16), 1 shl 16)).use { out ->
            val w = Writer(out)
            out.writeInt(MAGIC)
            out.writeInt(FORMAT_VERSION)
            info.write(w)
            out.writeInt(images.size)
            images.forEach { w.str(it) }
            out.writeInt(types.size)
            types.forEach { it.write(w) }
            out.writeInt(strings.size)
            strings.forEach { out.writeLong(it.address); w.str(it.value) }
        }
    }

    companion object {
        private const val MAGIC = 0x49324458 // "I2DX"
        private const val FORMAT_VERSION = 3
        const val FILE_NAME = "index.i2dx"

        fun load(file: File): DumpIndex {
            DataInputStream(BufferedInputStream(GZIPInputStream(FileInputStream(file), 1 shl 16), 1 shl 16)).use { input ->
                val r = Reader(input)
                require(input.readInt() == MAGIC) { "Not a dump index" }
                require(input.readInt() == FORMAT_VERSION) { "Unsupported dump index version" }
                val info = DumpInfo.read(r)
                val images = List(input.readInt()) { r.str() }
                val types = List(input.readInt()) { TypeEntry.read(r) }
                val strings = List(input.readInt()) { StringLiteralEntry(input.readLong(), r.str()) }
                return DumpIndex(info, images, types, strings)
            }
        }
    }

    /** String-interning writer: repeated strings (type names, modifiers) are stored once. */
    class Writer(val out: DataOutputStream) {
        private val table = HashMap<String, Int>()
        fun str(s: String?) {
            if (s == null) { out.writeInt(-1); return }
            val id = table[s]
            if (id != null) { out.writeInt(id); return }
            out.writeInt(-2)
            val bytes = s.toByteArray(Charsets.UTF_8)
            out.writeInt(bytes.size)
            out.write(bytes)
            table[s] = table.size
        }
    }

    class Reader(val input: DataInputStream) {
        private val table = ArrayList<String>()
        fun str(): String = strOrNull() ?: ""
        fun strOrNull(): String? {
            val id = input.readInt()
            if (id == -1) return null
            if (id >= 0) return table[id]
            val len = input.readInt()
            val bytes = ByteArray(len)
            input.readFully(bytes)
            val s = String(bytes, Charsets.UTF_8)
            table.add(s)
            return s
        }
    }
}

class DumpInfo(
    val title: String,
    val packageName: String,
    val unityVersion: String,
    val metadataVersion: String,
    val il2cppVersion: String,
    val format: String,
    val arch: String,
    val imageBase: Long,
    val codeRegistration: Long,
    val metadataRegistration: Long,
    val createdAt: Long,
    val is32Bit: Boolean,
    val isDumped: Boolean,
) {
    fun write(w: DumpIndex.Writer) {
        w.str(title); w.str(packageName); w.str(unityVersion); w.str(metadataVersion); w.str(il2cppVersion)
        w.str(format); w.str(arch)
        w.out.writeLong(imageBase); w.out.writeLong(codeRegistration); w.out.writeLong(metadataRegistration)
        w.out.writeLong(createdAt); w.out.writeBoolean(is32Bit); w.out.writeBoolean(isDumped)
    }

    companion object {
        fun read(r: DumpIndex.Reader) = DumpInfo(
            r.str(), r.str(), r.str(), r.str(), r.str(), r.str(), r.str(),
            r.input.readLong(), r.input.readLong(), r.input.readLong(), r.input.readLong(),
            r.input.readBoolean(), r.input.readBoolean()
        )
    }
}

enum class TypeKind { CLASS, STRUCT, ENUM, INTERFACE }

class TypeEntry(
    val typeDefIndex: Int,
    val imageIndex: Int,
    val namespace: String,
    /** Name as printed in dump.cs, nested types include the declaring type: Outer.Inner<T> */
    val name: String,
    val kind: TypeKind,
    val modifiers: String,
    val extends: String,
    val attributes: String,
    val fields: List<FieldEntry>,
    val properties: List<PropertyEntry>,
    val methods: List<MethodEntry>,
) {
    val fullName: String get() = if (namespace.isEmpty()) name else "$namespace.$name"

    fun write(w: DumpIndex.Writer) {
        val o = w.out
        o.writeInt(typeDefIndex); o.writeInt(imageIndex)
        w.str(namespace); w.str(name); o.writeByte(kind.ordinal); w.str(modifiers); w.str(extends); w.str(attributes)
        o.writeInt(fields.size); fields.forEach { it.write(w) }
        o.writeInt(properties.size); properties.forEach { it.write(w) }
        o.writeInt(methods.size); methods.forEach { it.write(w) }
    }

    companion object {
        fun read(r: DumpIndex.Reader): TypeEntry {
            val i = r.input
            val typeDefIndex = i.readInt()
            val imageIndex = i.readInt()
            val ns = r.str(); val name = r.str(); val kind = TypeKind.entries[i.readByte().toInt()]
            val mods = r.str(); val ext = r.str(); val attrs = r.str()
            val fields = List(i.readInt()) { FieldEntry.read(r) }
            val props = List(i.readInt()) { PropertyEntry.read(r) }
            val methods = List(i.readInt()) { MethodEntry.read(r) }
            return TypeEntry(typeDefIndex, imageIndex, ns, name, kind, mods, ext, attrs, fields, props, methods)
        }
    }
}

class FieldEntry(
    val name: String,
    val typeName: String,
    val modifiers: String,
    /** -1 when unknown; Int.MIN_VALUE for const fields (no offset printed). */
    val offset: Int,
    val defaultValue: String?,
    val isStatic: Boolean,
    val isConst: Boolean,
    val attributes: String,
) {
    fun write(w: DumpIndex.Writer) {
        w.str(name); w.str(typeName); w.str(modifiers); w.out.writeInt(offset); w.str(defaultValue)
        w.out.writeBoolean(isStatic); w.out.writeBoolean(isConst); w.str(attributes)
    }

    companion object {
        fun read(r: DumpIndex.Reader) = FieldEntry(
            r.str(), r.str(), r.str(), r.input.readInt(), r.strOrNull(), r.input.readBoolean(), r.input.readBoolean(), r.str()
        )
    }
}

class PropertyEntry(val name: String, val typeName: String, val modifiers: String, val hasGet: Boolean, val hasSet: Boolean) {
    fun write(w: DumpIndex.Writer) {
        w.str(name); w.str(typeName); w.str(modifiers); w.out.writeBoolean(hasGet); w.out.writeBoolean(hasSet)
    }

    companion object {
        fun read(r: DumpIndex.Reader) = PropertyEntry(r.str(), r.str(), r.str(), r.input.readBoolean(), r.input.readBoolean())
    }
}

class ParamEntry(val name: String, val typeName: String, val prefix: String) {
    override fun toString(): String = "$prefix$typeName $name"
}

class GenericInstanceEntry(val rva: Long, val offset: Long, val va: Long, val names: List<String>)

class MethodEntry(
    val name: String,
    val returnType: String,
    val params: List<ParamEntry>,
    val modifiers: String,
    val rva: Long,
    val offset: Long,
    val va: Long,
    val slot: Int,
    val isStatic: Boolean,
    val isAbstract: Boolean,
    val attributes: String,
    val genericInstances: List<GenericInstanceEntry>,
    /** C signature from script.json style naming (may be empty). */
    val token: Long,
) {
    val hasBody: Boolean get() = rva >= 0

    fun signature(): String = "$modifiers$returnType $name(${params.joinToString(", ")})"

    fun write(w: DumpIndex.Writer) {
        val o = w.out
        w.str(name); w.str(returnType)
        o.writeInt(params.size); params.forEach { w.str(it.name); w.str(it.typeName); w.str(it.prefix) }
        w.str(modifiers); o.writeLong(rva); o.writeLong(offset); o.writeLong(va); o.writeInt(slot)
        o.writeBoolean(isStatic); o.writeBoolean(isAbstract); w.str(attributes)
        o.writeInt(genericInstances.size)
        genericInstances.forEach { g ->
            o.writeLong(g.rva); o.writeLong(g.offset); o.writeLong(g.va)
            o.writeInt(g.names.size); g.names.forEach { w.str(it) }
        }
        o.writeLong(token)
    }

    companion object {
        fun read(r: DumpIndex.Reader): MethodEntry {
            val i = r.input
            val name = r.str(); val ret = r.str()
            val params = List(i.readInt()) { ParamEntry(r.str(), r.str(), r.str()) }
            val mods = r.str(); val rva = i.readLong(); val off = i.readLong(); val va = i.readLong(); val slot = i.readInt()
            val isStatic = i.readBoolean(); val isAbstract = i.readBoolean(); val attrs = r.str()
            val gens = List(i.readInt()) {
                val grva = i.readLong(); val goff = i.readLong(); val gva = i.readLong()
                GenericInstanceEntry(grva, goff, gva, List(i.readInt()) { r.str() })
            }
            val token = i.readLong()
            return MethodEntry(name, ret, params, mods, rva, off, va, slot, isStatic, isAbstract, attrs, gens, token)
        }
    }
}

class StringLiteralEntry(val address: Long, val value: String)
