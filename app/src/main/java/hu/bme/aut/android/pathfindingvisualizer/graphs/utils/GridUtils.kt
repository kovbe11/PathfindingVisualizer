package hu.bme.aut.android.pathfindingvisualizer.graphs.utils

import android.util.Log
import hu.bme.aut.android.pathfindingvisualizer.graphs.MutableWeightedEdge
import hu.bme.aut.android.pathfindingvisualizer.graphs.MutableWeightedGraph
import hu.bme.aut.android.pathfindingvisualizer.graphs.Node
import hu.bme.aut.android.pathfindingvisualizer.graphs.SimpleMutableWeightedEdge
import hu.bme.aut.android.pathfindingvisualizer.sqlite.Cell
import hu.bme.aut.android.pathfindingvisualizer.sqlite.Cell.Type.WALL

private fun weightLookup(src: Cell, dst: Cell): Int? {
    return when (dst.type) {
        Cell.Type.WEIGHT -> 3
        WALL -> null
        else -> if (src.type == Cell.Type.WEIGHT) 3 else 1
    }
}

fun gridGraphBuilder(cells: Set<Cell>): MutableWeightedGraph<Cell, Int> {
    val mapping: MutableMap<Pair<Int, Int>, Cell> = mutableMapOf()
    var maxRow = 0
    var maxCol = 0
    return mutableWeightedGraph {
        cells.forEach {
            maxRow = Integer.max(maxRow, it.row)
            maxCol = Integer.max(maxCol, it.col)
            mapping[it.row to it.col] = it
            node(it)
        }

        fun makeEdgeData(neighbourCoord: Pair<Int, Int>, current: Cell): Pair<Cell, Int>? {

            val neighbour = mapping[neighbourCoord]
            if (neighbour == null) {
                Log.println(Log.ERROR, "error", "$neighbour, $neighbourCoord,$mapping")
                error("fuck")
            }

            val weight = weightLookup(current, neighbour) ?: return null
            return neighbour to weight
        }

        for (row in 0..maxRow) {
            for (col in 0..maxCol) {
                val current = mapping[row to col]!!
                if (current.type == WALL) continue
                if (row + 1 <= maxRow) {
                    val edgeData = makeEdgeData((row + 1) to col, current)
                    edgeData?.let {
                        edge(current to edgeData.first, edgeData.second)
                    }
                }
                if (row - 1 >= 0) {
                    val edgeData = makeEdgeData((row - 1) to col, current)
                    edgeData?.let {
                        edge(current to edgeData.first, edgeData.second)
                    }
                }
                if (col + 1 <= maxCol) {
                    val edgeData = makeEdgeData(row to (col + 1), current)
                    edgeData?.let {
                        edge(current to edgeData.first, edgeData.second)
                    }
                }
                if (col - 1 >= 0) {
                    val edgeData = makeEdgeData(row to (col - 1), current)
                    edgeData?.let {
                        edge(current to edgeData.first, edgeData.second)
                    }
                }
            }
        }
    }

}

private fun findNeighbours(graph: MutableWeightedGraph<Cell, Int>, cell: Cell): List<Node<Cell>> {

    val neighbourCoords = listOf(
        cell.row + 1 to cell.col,
        cell.row - 1 to cell.col,
        cell.row to cell.col + 1,
        cell.row to cell.col - 1
    )

    return graph.nodes.filter {
        val itsCoords = it.value.row to it.value.col
        neighbourCoords.contains(itsCoords)
    }
}

@Suppress("UNCHECKED_CAST")
private fun findEdgesWithNeighbour(
    graph: MutableWeightedGraph<Cell, Int>,
    cell: Cell,
    neighbour: Node<Cell>
): Pair<MutableWeightedEdge<Cell, Int>?, MutableWeightedEdge<Cell, Int>?> {
    val inEdge = graph[neighbour]!!.find { it.end.value == cell } as? MutableWeightedEdge<Cell, Int>
    val outEdge =
        graph[cell.mutableNode]!!.find { it.end == neighbour } as? MutableWeightedEdge<Cell, Int>
    return inEdge to outEdge
}

fun MutableWeightedGraph<Cell, Int>.weightAdded(cell: Cell) {

    val neighbours = findNeighbours(this, cell)

    for (neighbour in neighbours) {
        if (neighbour.value.type == WALL || neighbour.value.type == Cell.Type.WEIGHT) continue

        val edges = findEdgesWithNeighbour(this, cell, neighbour)

        edges.first?.weight = 3
        edges.second?.weight = 3
    }
}

fun MutableWeightedGraph<Cell, Int>.weightRemoved(cell: Cell) {

    val neighbours = findNeighbours(this, cell)

    for (neighbour in neighbours) {
        if (neighbour.value.type == WALL || neighbour.value.type == Cell.Type.WEIGHT) continue

        val edges = findEdgesWithNeighbour(this, cell, neighbour)

        edges.first?.weight = 1
        edges.second?.weight = 1
    }
}

fun MutableWeightedGraph<Cell, Int>.wallAdded(cell: Cell) {
    cell.type = WALL
    val relevantEdges = edges.filter { it.start.value.type == WALL || it.end.value.type == WALL }
    relevantEdges.forEach { removeEdge(it) }

}

fun MutableWeightedGraph<Cell, Int>.wallRemoved(cell: Cell) {

    val neighbours = findNeighbours(this, cell)

    for (neighbour in neighbours) {

        val weight = weightLookup(cell, neighbour.value)

        weight?.let {
            val edgeOut = SimpleMutableWeightedEdge(cell.mutableNode, neighbour.mutable, it)
            val edgeIn = SimpleMutableWeightedEdge(neighbour.mutable, cell.mutableNode, it)
            addEdge(edgeOut)
            addEdge(edgeIn)
        }

    }
}