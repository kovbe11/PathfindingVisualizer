package hu.bme.aut.android.pathfindingvisualizer.graphs.traversal

import hu.bme.aut.android.pathfindingvisualizer.graphs.Edge
import hu.bme.aut.android.pathfindingvisualizer.graphs.Graph
import hu.bme.aut.android.pathfindingvisualizer.graphs.Node
import hu.bme.aut.android.pathfindingvisualizer.graphs.utils.get
import hu.bme.aut.android.pathfindingvisualizer.graphs.utils.isUndirected


class DFSHelper<T>(
    private val graph: Graph<T>,
    startNode: Node<T>,
    val onEnter: (Node<T>) -> Unit = {},
    val onVisitedEdge: (Edge<T>) -> Unit = {},
    val onNonVisitedEdge: (Edge<T>) -> Unit = {},
    val onFinishing: (Node<T>) -> Unit = {}
) {
    private val visited: MutableSet<Node<T>> = HashSet()
    val nodesReached: Int


    init {
        dfs(startNode)
        nodesReached = visited.size
    }

    private fun dfs(current: Node<T>) {

        visited.add(current)
        onEnter(current)

        val childrenConnections = (graph[current] ?: error("invalid graph"))

        for (edge in childrenConnections) {

            //check if we already have it visited
            if (visited.contains(edge.end)) {
                onVisitedEdge(edge)
                continue
            }
            onNonVisitedEdge(edge)
            dfs(edge.end)
        }
        onFinishing(current)
    }
}


fun <T> dfsDirectedDetectCycleFrom(graph: Graph<T>, startNode: Node<T>): Boolean {
    val onStack: MutableSet<Node<T>> = HashSet()
    var directedCycleFound = false

    DFSHelper(graph,
        startNode,
        onEnter = { onStack.add(it) },
        onVisitedEdge = { if (onStack.contains(it.end)) directedCycleFound = true; return@DFSHelper },
        onFinishing = { onStack.remove(it) })

    return directedCycleFound
}

fun <T> dfsUndirectedDetectCycleFrom(graph: Graph<T>, startNode: Node<T>): Boolean {
    if (!graph.isUndirected) {
        return false
    }
    val previousNodeMapping: MutableMap<Node<T>, Edge<T>> = HashMap()
    val onStack: MutableSet<Node<T>> = HashSet()
    var unDirectedCycleFound = false

    DFSHelper(
        graph,
        startNode,
        onEnter = { onStack.add(it) },
        onVisitedEdge = {
            if (onStack.contains(it.end) && previousNodeMapping.values.none { edge ->
                    it.start == edge.end && it.end == edge.start
                }) {
                unDirectedCycleFound = true
                return@DFSHelper
            }
        },
        onNonVisitedEdge = { previousNodeMapping[it.end] = it },
        onFinishing = { onStack.remove(it) })

    return unDirectedCycleFound
}
