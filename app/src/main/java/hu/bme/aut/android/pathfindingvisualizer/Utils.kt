package hu.bme.aut.android.pathfindingvisualizer

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KProperty

class SetOnce<T> {
    private var value: T? = null

    operator fun getValue(thisRef: Any?, prop: KProperty<*>): T {
        return value ?: error("Value was not set before access!")
    }

    operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: T) {
        if (this.value == null) this.value = value else error("The value was already set!")
    }
}

class ConcurrentBuffer<T>(t: T? = null) {

    private val lock = Object()
    private var _value: T? = null
    private val invalid: AtomicBoolean = AtomicBoolean(true)

    init {
        if (t != null) {
            _value = t
            invalid.set(false)
        }
    }

    private fun getVal(): T {
        synchronized(lock) {
            while (invalid.get()) {
                lock.wait()
            }
        }
        return _value!!
    }

    private fun setVal(t: T) {
        synchronized(lock) {
            invalid.set(false)
            _value = t
            lock.notifyAll()
        }
    }

    var value: T
        get() = getVal()
        set(value) = setVal(value)

    fun invalidate() {
        synchronized(lock) {
            invalid.set(true)
        }
    }

}
