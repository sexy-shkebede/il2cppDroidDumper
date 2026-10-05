package dev.il2cpp.dumper.core.extra

/**
 * Compares two dumps (e.g. two versions of the same game) and reports what changed:
 * added/removed types and methods, moved RVAs and changed field offsets.
 */
object DumpDiff {
    enum class ChangeKind { ADDED, REMOVED, MOVED, CHANGED }

    data class MethodChange(
        val kind: ChangeKind,
        val typeName: String,
        val signature: String,
        val oldRva: Long,
        val newRva: Long,
    )

    data class FieldChange(
        val kind: ChangeKind,
        val typeName: String,
        val field: String,
        val oldOffset: Int,
        val newOffset: Int,
    )

    data class Result(
        val oldInfo: DumpInfo,
        val newInfo: DumpInfo,
        val addedTypes: List<String>,
        val removedTypes: List<String>,
        val methods: List<MethodChange>,
        val fields: List<FieldChange>,
        val unchangedMethods: Int,
    ) {
        val movedMethods: Int get() = methods.count { it.kind == ChangeKind.MOVED }
        val addedMethods: Int get() = methods.count { it.kind == ChangeKind.ADDED }
        val removedMethods: Int get() = methods.count { it.kind == ChangeKind.REMOVED }
    }

    private fun methodKey(m: MethodEntry): String =
        m.name + "(" + m.params.joinToString(",") { it.prefix + it.typeName } + ")" + m.returnType

    private fun typeKey(t: TypeEntry): String = t.fullName

    fun compare(old: DumpIndex, new: DumpIndex): Result {
        val oldTypes = LinkedHashMap<String, TypeEntry>()
        old.types.forEach { oldTypes.putIfAbsent(typeKey(it), it) }
        val newTypes = LinkedHashMap<String, TypeEntry>()
        new.types.forEach { newTypes.putIfAbsent(typeKey(it), it) }

        val added = newTypes.keys.filter { it !in oldTypes }
        val removed = oldTypes.keys.filter { it !in newTypes }
        val methodChanges = ArrayList<MethodChange>()
        val fieldChanges = ArrayList<FieldChange>()
        var unchanged = 0

        for ((key, nt) in newTypes) {
            val ot = oldTypes[key] ?: continue
            // methods: overloads are matched by full signature, duplicates by order
            val oldMethods = HashMap<String, ArrayDeque<MethodEntry>>()
            ot.methods.forEach { oldMethods.getOrPut(methodKey(it)) { ArrayDeque() }.addLast(it) }
            for (nm in nt.methods) {
                val om = oldMethods[methodKey(nm)]?.removeFirstOrNull()
                if (om == null) {
                    methodChanges.add(MethodChange(ChangeKind.ADDED, key, nm.signature(), -1, nm.rva))
                } else if (om.rva != nm.rva) {
                    methodChanges.add(MethodChange(ChangeKind.MOVED, key, nm.signature(), om.rva, nm.rva))
                } else {
                    unchanged++
                }
            }
            oldMethods.values.forEach { left ->
                left.forEach { om -> methodChanges.add(MethodChange(ChangeKind.REMOVED, key, om.signature(), om.rva, -1)) }
            }
            // fields by name
            val oldFields = ot.fields.associateBy { it.name }
            val newFieldNames = HashSet<String>()
            for (nf in nt.fields) {
                newFieldNames.add(nf.name)
                val of = oldFields[nf.name]
                when {
                    of == null -> fieldChanges.add(FieldChange(ChangeKind.ADDED, key, "${nf.typeName} ${nf.name}", Int.MIN_VALUE, nf.offset))
                    of.offset != nf.offset || of.typeName != nf.typeName ->
                        fieldChanges.add(FieldChange(ChangeKind.CHANGED, key, "${nf.typeName} ${nf.name}", of.offset, nf.offset))
                }
            }
            for (of in ot.fields) {
                if (of.name !in newFieldNames) fieldChanges.add(FieldChange(ChangeKind.REMOVED, key, "${of.typeName} ${of.name}", of.offset, Int.MIN_VALUE))
            }
        }
        return Result(old.info, new.info, added, removed, methodChanges, fieldChanges, unchanged)
    }

    /** Plain text report (shareable). */
    fun report(r: Result): String {
        fun hex(v: Long) = if (v < 0) "-" else "0x" + java.lang.Long.toHexString(v).uppercase()
        fun off(v: Int) = if (v == Int.MIN_VALUE) "-" else "0x" + Integer.toHexString(v).uppercase()
        val sb = StringBuilder()
        sb.append("Il2Cpp dump diff\n")
        sb.append("old: ${r.oldInfo.title} metadata v${r.oldInfo.metadataVersion} ${r.oldInfo.arch}\n")
        sb.append("new: ${r.newInfo.title} metadata v${r.newInfo.metadataVersion} ${r.newInfo.arch}\n\n")
        sb.append("types: +${r.addedTypes.size} / -${r.removedTypes.size}\n")
        sb.append("methods: moved ${r.movedMethods}, added ${r.addedMethods}, removed ${r.removedMethods}, unchanged ${r.unchangedMethods}\n")
        sb.append("fields changed: ${r.fields.size}\n\n")
        if (r.methods.isNotEmpty()) {
            sb.append("== Methods ==\n")
            for (m in r.methods.sortedWith(compareBy({ it.typeName }, { it.kind }))) {
                sb.append("[${m.kind}] ${m.typeName} :: ${m.signature}  ${hex(m.oldRva)} -> ${hex(m.newRva)}\n")
            }
        }
        if (r.fields.isNotEmpty()) {
            sb.append("\n== Fields ==\n")
            for (f in r.fields.sortedBy { it.typeName }) {
                sb.append("[${f.kind}] ${f.typeName} :: ${f.field}  ${off(f.oldOffset)} -> ${off(f.newOffset)}\n")
            }
        }
        if (r.addedTypes.isNotEmpty()) {
            sb.append("\n== Added types ==\n")
            r.addedTypes.forEach { sb.append(it).append('\n') }
        }
        if (r.removedTypes.isNotEmpty()) {
            sb.append("\n== Removed types ==\n")
            r.removedTypes.forEach { sb.append(it).append('\n') }
        }
        return sb.toString()
    }
}
