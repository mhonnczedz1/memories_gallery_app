package dev.build_with_mhonn.memories.core

/**
 * The app's single logging interface (E1.12). Release builds use [ReleaseLog], which drops
 * debug and info, so file paths, names, and queries logged at those levels never reach a
 * release device.
 */
interface AppLog {
    fun d(tag: String, msg: String)

    fun i(tag: String, msg: String)

    fun w(tag: String, msg: String)

    fun e(tag: String, msg: String)
}

/** Prints every level to standard output. Used by `:core` tests. */
object DebugLog : AppLog {
    override fun d(tag: String, msg: String) = println("D/$tag: $msg")

    override fun i(tag: String, msg: String) = println("I/$tag: $msg")

    override fun w(tag: String, msg: String) = println("W/$tag: $msg")

    override fun e(tag: String, msg: String) = println("E/$tag: $msg")
}

/** Drops debug and info; forwards warn and error to [delegate]. */
class ReleaseLog(private val delegate: AppLog) : AppLog {
    override fun d(tag: String, msg: String) = Unit

    override fun i(tag: String, msg: String) = Unit

    override fun w(tag: String, msg: String) = delegate.w(tag, msg)

    override fun e(tag: String, msg: String) = delegate.e(tag, msg)
}
