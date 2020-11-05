package hu.bme.aut.android.pathfindingvisualizer.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import hu.bme.aut.android.pathfindingvisualizer.MainActivity
import hu.bme.aut.android.pathfindingvisualizer.R
import hu.bme.aut.android.pathfindingvisualizer.sqlite.Cell
import hu.bme.aut.android.pathfindingvisualizer.sqlite.Cell.Type.*
import hu.bme.aut.android.pathfindingvisualizer.sqlite.Cell.VisitState.UNVISITED
import kotlin.concurrent.thread
import kotlin.reflect.KProperty

class SetOnce<T>{
    private var value: T? = null

    operator fun getValue(thisRef: Any?, prop: KProperty<*>): T{
        return value ?: error("Value was not set before access!")
    }

    operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: T) {
        if(this.value == null) this.value = value else error("The value was already set!")
    }
}


class GridView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    companion object {
        const val START_TYPE = 1
        const val END_TYPE = 2
        const val CLEAR_TYPE = 3
        const val WEIGHT_TYPE = 4
        const val WALL_TYPE = 5
        //TODO ez mi a retkes szarért nem enum amugy

        const val CELL_SIZE = 43

    }

    val GRID_PAINT = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2F
    }

    private var WALL_BITMAP by SetOnce<Bitmap>()
    private var WEIGHT_BITMAP by SetOnce<Bitmap>()
    private var START_NODE_BITMAP by SetOnce<Bitmap>()
    private var END_NODE_BITMAP by SetOnce<Bitmap>()

    var mainActivity: MainActivity by SetOnce()

    var currentCursorType = START_TYPE

    private var startNode: Cell? = null
    private var endNode: Cell? = null

    private var cells: MutableSet<Cell> = mutableSetOf()

    private var rowCount: Int = 0
    private var colCount: Int = 0
    private var colOffset: Int = 0
    private var rowOffset: Int = 0

    fun initializeColors(){
        val nodeColor = ContextCompat.getColor(context, R.color.nodeColor)
        val gridColor = ContextCompat.getColor(context, R.color.gridColor)

        WALL_BITMAP = ContextCompat.getDrawable(context, R.drawable.ic_wall)!!.apply {
            setTint(nodeColor)
        }.toBitmap(width = CELL_SIZE, height = CELL_SIZE)
        WEIGHT_BITMAP = ContextCompat.getDrawable(context, R.drawable.ic_weight)!!.apply {
            setTint(nodeColor)
        }.toBitmap(width = CELL_SIZE, height = CELL_SIZE)
        START_NODE_BITMAP = ContextCompat.getDrawable(context, R.drawable.ic_start_node)!!.apply{
            setTint(nodeColor)
        }.toBitmap(width = CELL_SIZE, height = CELL_SIZE)
        END_NODE_BITMAP = ContextCompat.getDrawable(context, R.drawable.ic_end_node)!!.apply{
            setTint(nodeColor)
        }.toBitmap(width = CELL_SIZE, height = CELL_SIZE)

        GRID_PAINT.color = gridColor
    }

    private fun calcGrid(rowRange: IntRange, colRange: IntRange){
        for (row in rowRange){
            for (col in colRange){
                val cell = Cell(row, col, NORMAL, UNVISITED)
                this.cells.add(cell)
                cell.inserted()
            }
        }
    }


    fun restoreObjects(
        cells: List<Cell>?
    ) {

        if (cells != null && cells.isNotEmpty()) {
            this.cells.addAll(cells)
            startNode = this.cells.find {
                it.type == START
            }
            endNode = this.cells.find {
                it.type == END
            }



        } else {

            colCount = width / CELL_SIZE
            rowCount = height / CELL_SIZE
            val choppedWidth = width - colCount * CELL_SIZE
            val choppedHeight = height - rowCount * CELL_SIZE

            val rowRange = if(choppedHeight == 0){
                rowOffset = 0
                0..rowCount
            }else{
                rowOffset = (height - rowCount * CELL_SIZE) / 2
                0 until rowCount
            }
            val colRange = if(choppedWidth == 0){
                colOffset = 0
                0..colCount
            }else{
                colOffset = (width - colCount * CELL_SIZE) / 2
                0 until colCount
            }

            calcGrid(rowRange, colRange)
        }
        invalidate()
    }




    private fun findCellByXY(x: Float, y: Float): Cell? {
//        col * size + colOffset,
//        row * size + rowOffset,
//        (col + 1) * size + colOffset,
//        (row + 1) * size + rowOffset

        return cells.find {
            val rect = it.getRect(CELL_SIZE, rowOffset, colOffset)
//            Log.println(Log.INFO, null, "$x, $y, ${rect.left}, ${rect.right}, ${rect.top}, ${rect.bottom}")
            rect.left < x && rect.right > x && rect.top < y && rect.bottom > y
        }
    }



    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if(canvas == null || cells == null) return //it is null on first draw.


        cells.forEach {
            val bitmap = when (it.type) {
                WEIGHT -> WEIGHT_BITMAP
                WALL -> WALL_BITMAP
                END -> END_NODE_BITMAP
                START -> START_NODE_BITMAP
                else -> null
            }
            bitmap?.let {bm ->
                canvas.drawBitmap(bm, null ,it.getRectF(CELL_SIZE, rowOffset, colOffset), null)
            }
            canvas.drawRect(it.getRect(CELL_SIZE, rowOffset, colOffset), GRID_PAINT)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return false
//                || event.action != MotionEvent.ACTION_UP
//        val cell = findCellByXY(event.x, event.y) ?: return false
        val cell = findCellByXY(event.x, event.y) ?: return false

        if (cell == endNode || cell == startNode) return false

        when (currentCursorType) {
            START_TYPE -> {
                startNode?.apply {
                    type = NORMAL
                    this.updated()
                }
                cell.type = START
                startNode = cell
            }
            END_TYPE -> {
                endNode?.apply {
                    type = NORMAL
                    this.updated()
                }
                cell.type = END
                endNode = cell
            }
            WEIGHT_TYPE -> cell.type = WEIGHT
            WALL_TYPE -> cell.type = WALL
            CLEAR_TYPE -> cell.type = NORMAL
        }
        cell.updated()
        Log.println(Log.INFO, null, cell.type.name)
        invalidate()
        return true
    }


    private fun Cell.updated(){
        thread {
            mainActivity.updateCell(this)
        }
    }

    private fun Cell.inserted(){
        thread {
            mainActivity.insertCell(this)
        }
    }


}

