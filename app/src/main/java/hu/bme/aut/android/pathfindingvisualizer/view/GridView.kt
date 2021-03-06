package hu.bme.aut.android.pathfindingvisualizer.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import hu.bme.aut.android.pathfindingvisualizer.*
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
import hu.bme.aut.android.pathfindingvisualizer.view.GridView.CursorType.*
import kotlin.concurrent.thread


class GridView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    enum class CursorType {
        START_TYPE,
        END_TYPE,
        CLEAR_TYPE,
        WEIGHT_TYPE,
        WALL_TYPE
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

    private lateinit var WALL_BITMAP: Bitmap
    private lateinit var WEIGHT_BITMAP: Bitmap
    private lateinit var START_NODE_BITMAP: Bitmap
    private lateinit var END_NODE_BITMAP: Bitmap

    var mainActivity: MainActivity by SetOnce()

    var currentCursorType = START_TYPE
    var currentAlgorithmType = BELLMANFORD

    private var startNode: Cell? = null
    private var endNode: Cell? = null

    private var cells: MutableSet<Cell> = mutableSetOf()

    private var cellSize: Int = -1
    private var rowCount: Int = 0
    private var colCount: Int = 0
    private var colOffset: Int = 0
    private var rowOffset: Int = 0

    private var graph: MutableWeightedGraph<Cell, Int>? by ConcurrentBlockingBuffer()
    private val algorithmRunner = StartsOnlyOnce()

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
            val rect = it.getRect(cellSize, rowOffset, colOffset)
//            Log.println(Log.INFO, null, "$x, $y, ${rect.left}, ${rect.right}, ${rect.top}, ${rect.bottom}")
            rect.left < x && rect.right > x && rect.top < y && rect.bottom > y
        }
    }


    fun initializeColors(gridSize: Int) {
        cellSize = gridSize
        val nodeColor = ContextCompat.getColor(context, R.color.nodeColor)
        val gridColor = ContextCompat.getColor(context, R.color.gridColor)
        val visitedColor = ContextCompat.getColor(context, R.color.visitedColor)
        val usedColor = ContextCompat.getColor(context, R.color.usedColor)

        WALL_BITMAP = ContextCompat.getDrawable(context, R.drawable.ic_wall)!!.apply {
            setTint(nodeColor)
        }.toBitmap(width = cellSize, height = cellSize)
        WEIGHT_BITMAP = ContextCompat.getDrawable(context, R.drawable.ic_weight)!!.apply {
            setTint(nodeColor)
        }.toBitmap(width = cellSize, height = cellSize)
        START_NODE_BITMAP = ContextCompat.getDrawable(context, R.drawable.ic_start_node)!!.apply {
            setTint(nodeColor)
        }.toBitmap(width = cellSize, height = cellSize)
        END_NODE_BITMAP = ContextCompat.getDrawable(context, R.drawable.ic_end_node)!!.apply {
            setTint(nodeColor)
        }.toBitmap(width = cellSize, height = cellSize)

        GRID_PAINT.color = gridColor
        VISITED_PAINT.color = visitedColor
        USED_PAINT.color = usedColor
    }


    fun restoreObjects(
        cells: List<Cell>?,
        gridSize: Int
    ) {
        cellSize = gridSize
        colCount = width / cellSize
        rowCount = height / cellSize


        val choppedWidth = width - colCount * cellSize
        val choppedHeight = height - rowCount * cellSize

        var rowsTemp = 0 //used to check if cell_size changed since last restore
        var colsTemp = 0 //used to check if cell_size changed since last restore

        val rowRange = if (choppedHeight == 0) {
            rowOffset = 0
            rowsTemp += rowCount + 1
            0..rowCount
        } else {
            rowOffset = (height - rowCount * cellSize) / 2
            rowsTemp += rowCount
            0 until rowCount
        }
        val colRange = if (choppedWidth == 0) {
            colOffset = 0
            colsTemp += colCount + 1
            0..colCount
        } else {
            colOffset = (width - colCount * cellSize) / 2
            colsTemp += colCount
            0 until colCount
        }

        if (cells != null && cells.isNotEmpty()) {
            this.cells.clear()

            if (cells.size != colsTemp * rowsTemp) {
                mainActivity.clearCells()
                calcGrid(rowRange, colRange)
            } else {
                this.cells.addAll(cells)
                startNode = this.cells.find {
                    it.type == START
                }
                endNode = this.cells.find {
                    it.type == END
                }
            }

        } else {
            calcGrid(rowRange, colRange)
        }
        graph = null
        thread {
            graph = gridGraphBuilder(this.cells)
        }
        invalidate()

    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        @Suppress("SENSELESS_COMPARISON") //it is null on first draw.
        if (canvas == null || cells == null) return


        cells.forEach {
            val rect = it.getRect(cellSize, rowOffset, colOffset)

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
                canvas.drawBitmap(bm, null, it.getRectF(cellSize, rowOffset, colOffset), null)
            }

        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return false
//        val cell = findCellByXY(event.x, event.y) ?: return false
        val cell = findCellByXY(event.x, event.y) ?: return false

        if (cell == endNode || cell == startNode) return false

        graph?.let { graph ->
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

        return false

    }


    fun showAlgorithm() {

        startNode?.let { start ->
            endNode?.let { finish ->
                graph?.let { graph ->
                    algorithmRunner.runOnOtherThread {
                        val cells = this.cells
                        Log.i("algo", "${currentAlgorithmType.name} is running")
                        cells.forEach {
                            it.visitState = UNVISITED
                        }
                        invalidate()
                        val visitedNodes = mutableListOf<Cell>()
                        val shortestPath =
                            currentAlgorithmType.execute(graph, start.node, finish.node) {
                                visitedNodes.add(it.value)
                            }

                        visitedNodes.forEach {
                            it.visitState = VISITED
                            invalidate()
                            Thread.sleep(40)
                        }
                        shortestPath?.let { path ->
                            path.nodes.forEach {
                                visitedNodes.remove(it.value)
                                it.value.visitState = USED
                            }
                        }
                        invalidate()
                        cells.forEach {
                            it.updated()
                        }
                    }
                }
            }
        }

    }

}




