package dev.jotalac.core

/** An error returned by the Go core. */
class CoreException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    companion object {
        fun from(throwable: Throwable): CoreException =
            throwable as? CoreException ?: CoreException(throwable.message ?: throwable.toString(), throwable)
    }
}
