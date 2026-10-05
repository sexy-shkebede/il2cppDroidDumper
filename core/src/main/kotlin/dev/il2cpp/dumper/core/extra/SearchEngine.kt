package dev.il2cpp.dumper.core.extra

/** Fast in-memory search over a [DumpIndex] for the class browser. */
class SearchEngine(private val index: DumpIndex) {
    enum class Scope { ALL, TYPES, METHODS, FIELDS, STRINGS }

    sealed class Hit {
        abstract val type: TypeEntry?

        data class TypeHit(override val type: TypeEntry) : Hit()
        data class MethodHit(override val type: TypeEntry, val method: MethodEntry) : Hit()
        data class FieldHit(override val type: TypeEntry, val field: FieldEntry) : Hit()
        data class StringHit(val literal: StringLiteralEntry) : Hit() {
            override val type: TypeEntry? get() = null
        }
    }

    private val lowerTypeNames = index.types.map { it.fullName.lowercase() }

    /**
     * Query syntax: plain text (case-insensitive substring), `0x1234` finds methods by RVA,
     * `/regex/` uses a regular expression.
     */
    fun search(query: String, scope: Scope = Scope.ALL, limit: Int = 500): List<Hit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<Hit>()
        val rva = if (q.startsWith("0x", ignoreCase = true)) q.substring(2).toLongOrNull(16) else null
        val regex = if (q.length > 2 && q.startsWith("/") && q.endsWith("/")) runCatching { Regex(q.substring(1, q.length - 1), RegexOption.IGNORE_CASE) }.getOrNull() else null
        val lower = q.lowercase()
        fun match(s: String): Boolean = regex?.containsMatchIn(s) ?: s.lowercase().contains(lower)

        if (rva != null) {
            for (t in index.types) for (m in t.methods) {
                if (m.rva == rva || m.offset == rva || (m.genericInstances.any { it.rva == rva })) out.add(Hit.MethodHit(t, m))
                if (out.size >= limit) return out
            }
            for (t in index.types) for (f in t.fields) {
                if (f.offset.toLong() == rva && scope in setOf(Scope.ALL, Scope.FIELDS)) out.add(Hit.FieldHit(t, f))
                if (out.size >= limit) return out
            }
            return out
        }
        if (scope == Scope.ALL || scope == Scope.TYPES) {
            for ((i, t) in index.types.withIndex()) {
                if (regex?.containsMatchIn(t.fullName) ?: lowerTypeNames[i].contains(lower)) out.add(Hit.TypeHit(t))
                if (out.size >= limit) return out
            }
        }
        if (scope == Scope.ALL || scope == Scope.METHODS) {
            for (t in index.types) for (m in t.methods) {
                if (match(m.name)) out.add(Hit.MethodHit(t, m))
                if (out.size >= limit) return out
            }
        }
        if (scope == Scope.ALL || scope == Scope.FIELDS) {
            for (t in index.types) for (f in t.fields) {
                if (match(f.name)) out.add(Hit.FieldHit(t, f))
                if (out.size >= limit) return out
            }
        }
        if (scope == Scope.ALL || scope == Scope.STRINGS) {
            for (s in index.strings) {
                if (match(s.value)) out.add(Hit.StringHit(s))
                if (out.size >= limit) return out
            }
        }
        return out
    }

    /** Namespace tree for browsing: namespace -> types (sorted). */
    fun namespaces(): List<Pair<String, List<TypeEntry>>> =
        index.types.groupBy { it.namespace }.toSortedMap().map { (ns, types) -> ns to types.sortedBy { it.name } }
}
