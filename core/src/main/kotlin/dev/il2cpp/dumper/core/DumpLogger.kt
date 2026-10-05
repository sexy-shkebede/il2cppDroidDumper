package dev.il2cpp.dumper.core

/** Replacement for Console.WriteLine of the original tool. */
fun interface DumpLogger {
    fun log(message: String)

    companion object {
        val NONE = DumpLogger { }
        val STDOUT = DumpLogger { println(it) }
    }
}

/** Progress reporting + cooperative cancellation for long running steps. */
interface DumpProgress {
    /** [fraction] in 0..1 or -1 for indeterminate. */
    fun onProgress(stage: String, fraction: Float) {}

    /** Throw (e.g. CancellationException) to abort. */
    fun checkCancelled() {}

    companion object {
        val NONE = object : DumpProgress {}
    }
}
