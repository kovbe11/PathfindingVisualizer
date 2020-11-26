package hu.bme.aut.android.pathfindingvisualizer

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.reflect.KProperty

class SetOnce<T> {
    private var value: T? = null

    operator fun getValue(thisRef: Any?, prop: KProperty<*>): T {
        return value ?: error("Value was not set before access!")
    }

    operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: T) {
        if (this.value == value) return
        if (this.value == null) this.value = value else error("The value was already set!")
    }
}

class ConcurrentBlockingBuffer<T>(t: T? = null) {
    private var _value: T? = t
    private val lock = Object()

    //blocks thread until value is set
    operator fun getValue(thisRef: Any?, prop: KProperty<*>): T {
        return synchronized(lock) {
            while (_value == null) {
                lock.wait()
            }
            _value ?: error("value was set to null again after being set")
        }
    }

    operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: T?) {
        synchronized(lock) {
            _value = value
            lock.notifyAll()
        }
    }
}

class StartsOnlyOnce {

    private val locked = AtomicBoolean(false)

    fun runOnOtherThread(block: () -> Unit) {
        if (locked.compareAndSet(false, true)) {
            thread {
                block()
                locked.set(false)
            }
        }
    }

}
