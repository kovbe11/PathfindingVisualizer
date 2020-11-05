package hu.bme.aut.android.pathfindingvisualizer.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import hu.bme.aut.android.pathfindingvisualizer.ConcurrentBuffer
import hu.bme.aut.android.pathfindingvisualizer.MainActivity
import hu.bme.aut.android.pathfindingvisualizer.R
import hu.bme.aut.android.pathfindingvisualizer.SetOnce
import hu.bme.aut.android.pathfindingvisualizer.graphs.MutableWeightedGraph
import hu.bme.aut.android.pathfindingvisualizer.graphs.Node
import hu.bme.aut.android.pathfindingvisualizer.graphs.WeightedGraph
import hu.bme.aut.android.pathfindingvisualizer.graphs.algorithms.aStarShortestPathOrNull
import hu.bme.aut.android.pathfindingvisualizer.graphs.algorithms.bellmanFordShortestPathOrNull
import hu.bme.aut.android.pathfindingvisualizer.graphs.algorithms.dijkstraShortestPathOrNull
import hu.bme.aut.android.pathfindingvisualizer.graphs.utils.*
import hu.bme.aut.android.pathfindingvisualizer.sqlite.Cell
import hu.bme.aut.android.pathfindingvisualizer.sqlite.Cell.Type.*
import hu.bme.aut.android.pathfindingvisualizer.sqlite.Cell.VisitState.*
import hu.bme.aut.android.pathfindingvisualizer.view.GridView.Algorithms.BELLMANFORD
import kotlin.concurrent.thread


class GridView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    companion object {
        const val START_TYPE = 1
        const val END_TYPE = 2
        const val CLEAR_TYPE = 3
        const val WEIGHT_TYPE = 4
        const val WALL_TYPE = 5
        //TODO ez mi a retkes szarért nem enum amugy

        const val CELL_SIZE = 40

    }

    enum class Algorithms {
        ASTAR {
            override fun execute(
                graph: WeightedGraph<Cell, Int>,
                startNode: Node<Cell>,
                endNode: Node<Cell>, onNodeVisited: (Node<Cell>) -> Unit
            ): WeightedGraph<Cell, Int>? {
                val heuristicFn: (Node<Cell>) -> Double = {
                    kotlin.math.sqrt(((it.value.row - endNode.value.row) * (it.value.row - endNode.value.row) + (it.value.col - endNode.value.col) * (it.value.col - endNode.value.col)).toDouble())
                }
                return aStarShortestPathOrNull(
                    graph,
                    startNode,
                    endNode,
                    heuristicFn,
                    IntAdapter,
                    onNodeVisited
                )
            }
        },
        DIJKSTRA {
            override fun execute(
                graph: WeightedGraph<Cell, Int>,
                startNode: Node<Cell>,
                endNode: Node<Cell>, onNodeVisited: (Node<Cell>) -> Unit
            ): WeightedGraph<Cell, Int>? {
                return dijkstraShortestPathOrNull(
                    graph,
                    startNode,
                    endNode,
                    IntAdapter,
                    onNodeVisited
                )
            }
        },
        BELLMANFORD {
            override fun execute(
                graph: WeightedGraph<Cell, Int>,
                startNode: Node<Cell>,
                endNode: Node<Cell>,
                onNodeVisited: (Node<Cell>) -> Unit
            ): WeightedGraph<Cell, Int>? {
                return bellmanFordShortestPathOrNull(
                    graph,
                    startNode,
                    endNode,
                    IntAdapter,
                    onNodeVisited
                )
            }
        };

        abstract fun execute(
            graph: WeightedGraph<Cell, Int>,
            startNode: Node<Cell>,
            endNode: Node<Cell>,
            onNodeVisited: (Node<Cell>) -> Unit
        ): WeightedGraph<Cell, Int>?

    }

    private val GRID_PAINT = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2F
    }
    private val VISITED_PAINT = Paint().apply {
        style = Paint.Style.FILL
    }
    private val USED_PAINT = Paint().apply {
        style = Paint.Style.FILL
    }

    private var WALL_BITMAP by SetOnce<Bitmap>()
    private var WEIGHT_BITMAP by SetOnce<Bitmap>()
    private var START_NODE_BITMAP by SetOnce<Bitmap>()
    private var END_NODE_BITMAP by SetOnce<Bitmap>()

    var mainActivity: MainActivity by SetOnce()

    var currentCursorType = START_TYPE
    var currentAlgorithmType = BELLMANFORD

    private var startNode: Cell? = null
    private var endNode: Cell? = null

    private var cells: MutableSet<Cell> = mutableSetOf()

    private var rowCount: Int = 0
    private var colCount: Int = 0
    private var colOffset: Int = 0
    private var rowOffset: Int = 0

    private val graph: ConcurrentBuffer<MutableWeightedGraph<Cell, Int>> = ConcurrentBuffer()

    private fun ConcurrentBuffer<MutableWeightedGraph<Cell, Int>>.weightRemoved(cell: Cell) {
        value.weightRemoved(cell)
    }

    private fun ConcurrentBuffer<MutableWeightedGraph<Cell, Int>>.weightAdded(cell: Cell) {
        value.weightAdded(cell)
    }

    private fun ConcurrentBuffer<MutableWeightedGraph<Cell, Int>>.wallRemoved(cell: Cell) {
        value.wallRemoved(cell)
    }

    private fun ConcurrentBuffer<MutableWeightedGraph<Cell, Int>>.wallAdded(cell: Cell) {
        value.wallAdded(cell)
    }


    private fun Cell.updated() {
        thread {
            mainActivity.updateCell(this)
        }
    }

    private fun Cell.inserted() {
        thread {
            mainActivity.insertCell(this)
        }
    }


    private fun calcGrid(rowRange: IntRange, colRange: IntRange) {
        for (row in rowRange) {
            for (col in colRange) {
                val cell = Cell(row, col, NORMAL, UNVISITED)
                this.cells.add(cell)
                cell.inserted()
            }
        }
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


    fun initializeColors() {
        val nodeColor = ContextCompat.getColor(context, R.color.nodeColor)
        val gridColor = ContextCompat.getColor(context, R.color.gridColor)
        val visitedColor = ContextCompat.getColor(context, R.color.visitedColor)
        val usedColor = ContextCompat.getColor(context, R.color.usedColor)

        WALL_BITMAP = ContextCompat.getDrawable(context, R.drawable.ic_wall)!!.apply {
            setTint(nodeColor)
        }.toBitmap(width = CELL_SIZE, height = CELL_SIZE)
        WEIGHT_BITMAP = ContextCompat.getDrawable(context, R.drawable.ic_weight)!!.apply {
            setTint(nodeColor)
        }.toBitmap(width = CELL_SIZE, height = CELL_SIZE)
        START_NODE_BITMAP = ContextCompat.getDrawable(context, R.drawable.ic_start_node)!!.apply {
            setTint(nodeColor)
        }.toBitmap(width = CELL_SIZE, height = CELL_SIZE)
        END_NODE_BITMAP = ContextCompat.getDrawable(context, R.drawable.ic_end_node)!!.apply {
            setTint(nodeColor)
        }.toBitmap(width = CELL_SIZE, height = CELL_SIZE)

        GRID_PAINT.color = gridColor
        VISITED_PAINT.color = visitedColor
        USED_PAINT.color = usedColor
    }


    fun restoreObjects(
        cells: List<Cell>?
    ) {
        colCount = width / CELL_SIZE
        rowCount = height / CELL_SIZE

        val choppedWidth = width - colCount * CELL_SIZE
        val choppedHeight = height - rowCount * CELL_SIZE

        val rowRange = if (choppedHeight == 0) {
            rowOffset = 0
            0..rowCount
        } else {
            rowOffset = (height - rowCount * CELL_SIZE) / 2
            0 until rowCount
        }
        val colRange = if (choppedWidth == 0) {
            colOffset = 0
            0..colCount
        } else {
            colOffset = (width - colCount * CELL_SIZE) / 2
            0 until colCount
        }

        if (cells != null && cells.isNotEmpty()) {
            this.cells.addAll(cells)
            startNode = this.cells.find {
                it.type == START
            }
            endNode = this.cells.find {
                it.type == END
            }

        } else {
            calcGrid(rowRange, colRange)
        }
        graph.invalidate()
        thread {
            graph.value = gridGraphBuilder(this.cells)
        }
        invalidate()

    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        @Suppress("SENSELESS_COMPARISON") //it is null on first draw.
        if (canvas == null || cells == null) return


        cells.forEach {
            val rect = it.getRect(CELL_SIZE, rowOffset, colOffset)

            @Suppress("NON_EXHAUSTIVE_WHEN")
            when (it.visitState) {
                VISITED -> {
                    canvas.drawRect(rect, VISITED_PAINT)
                }
                USED -> {
                    canvas.drawRect(rect, USED_PAINT)
                }
            }
            canvas.drawRect(rect, GRID_PAINT)


            val bitmap = when (it.type) {
                WEIGHT -> WEIGHT_BITMAP
                WALL -> WALL_BITMAP
                END -> END_NODE_BITMAP
                START -> START_NODE_BITMAP
                else -> null
            }
            bitmap?.let { bm ->
                canvas.drawBitmap(bm, null, it.getRectF(CELL_SIZE, rowOffset, colOffset), null)
            }

        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return false
//                || event.action != MotionEvent.ACTION_UP
//        val cell = findCellByXY(event.x, event.y) ?: return false
        val cell = findCellByXY(event.x, event.y) ?: return false

        if (cell == endNode || cell == startNode) return false

        //TODO: currentCursorType.clicked(cell)
        when (currentCursorType) {
            START_TYPE -> {
                startNode?.apply {
                    type = NORMAL
                    this.updated()
                }
                @Suppress("NON_EXHAUSTIVE_WHEN")
                when (cell.type) {
                    WEIGHT -> graph.weightRemoved(cell)
                    WALL -> graph.wallRemoved(cell)
                }

                cell.type = START
                startNode = cell
            }
            END_TYPE -> {
                endNode?.apply {
                    type = NORMAL
                    this.updated()
                }
                @Suppress("NON_EXHAUSTIVE_WHEN")
                when (cell.type) {
                    WEIGHT -> graph.weightRemoved(cell)
                    WALL -> graph.wallRemoved(cell)
                }

                cell.type = END
                endNode = cell
            }
            WEIGHT_TYPE -> {
                @Suppress("NON_EXHAUSTIVE_WHEN")
                when (cell.type) {
                    WEIGHT -> return false
                    WALL -> graph.wallRemoved(cell)
                }
                graph.weightAdded(cell)
                cell.type = WEIGHT
            }
            WALL_TYPE -> {
                @Suppress("NON_EXHAUSTIVE_WHEN")
                when (cell.type) {
                    WEIGHT -> graph.weightRemoved(cell)
                    WALL -> return false
                }
                graph.wallAdded(cell)
                //cell.type = WALL is done in wallAdded
            }
            CLEAR_TYPE -> {
                @Suppress("NON_EXHAUSTIVE_WHEN")
                when (cell.type) {
                    WEIGHT -> graph.weightRemoved(cell)
                    WALL -> graph.wallRemoved(cell)
                }
                cell.type = NORMAL
            }
        }
        cell.updated()

        invalidate()
        return true
    }

    fun showAlgo() {

        startNode?.let { start ->
            endNode?.let { finish ->
                cells.forEach {
                    it.visitState = UNVISITED
                }
                invalidate()
                val visitedNodes = mutableListOf<Cell>()
                val shortestPath =
                    currentAlgorithmType.execute(graph.value, start.node, finish.node) {
                        visitedNodes.add(it.value)
                    }
                shortestPath?.let { path ->
                    path.nodes.forEach {
                        visitedNodes.remove(it.value)
                        it.value.visitState = USED
                    }
                }
                visitedNodes.forEach {
                    it.visitState = VISITED
                }
                cells.forEach {
                    it.updated()
                }
                invalidate()
            }
        }
    }

}




