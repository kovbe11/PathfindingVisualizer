package hu.bme.aut.android.pathfindingvisualizer.sqlite

import android.graphics.Rect
import android.graphics.RectF
import androidx.room.Entity
import androidx.room.TypeConverter

@Entity(tableName = "Cells", primaryKeys = ["row", "col"])
data class Cell(
    val row: Int,
    val col: Int,
    var type: Type,
    var visitState: VisitState
) {
    enum class Type {
        START, END, WEIGHT, WALL, NORMAL
    }

    enum class VisitState {
        VISITED, UNVISITED, USED
    }

    fun getRect(size: Int, rowOffset: Int, colOffset: Int): Rect {
        return Rect(
            col * size + colOffset,
            row * size + rowOffset,
            (col + 1) * size + colOffset,
            (row + 1) * size + rowOffset
        )
    }

    fun getRectF(size: Int, rowOffset: Int, colOffset: Int): RectF{
        val floatOffset = size * 0.1F
        return RectF(
            (col * size + colOffset) + floatOffset,
            (row * size + rowOffset) + floatOffset,
            ((col + 1) * size + colOffset) - floatOffset,
            ((row + 1) * size + rowOffset) - floatOffset
        )
    }

}

object Converters {
    @TypeConverter
    @JvmStatic
    fun toType(ordinal: Int): Cell.Type? {
        return Cell.Type.values().find {
            it.ordinal == ordinal
        }
    }

    @JvmStatic
    @TypeConverter
    fun toIntType(type: Cell.Type): Int {
        return type.ordinal
    }

    @TypeConverter
    @JvmStatic
    fun toVisitState(ordinal: Int): Cell.VisitState? {
        return Cell.VisitState.values().find {
            it.ordinal == ordinal
        }
    }

    @JvmStatic
    @TypeConverter
    fun toIntVisitState(type: Cell.VisitState): Int {
        return type.ordinal
    }

}