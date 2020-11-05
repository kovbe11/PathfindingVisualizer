package hu.bme.aut.android.pathfindingvisualizer.graphs.algorithms

import hu.bme.aut.android.pathfindingvisualizer.graphs.Node
import hu.bme.aut.android.pathfindingvisualizer.graphs.WeightedEdge
import hu.bme.aut.android.pathfindingvisualizer.graphs.WeightedGraph
import hu.bme.aut.android.pathfindingvisualizer.graphs.utils.NumberAdapter
import hu.bme.aut.android.pathfindingvisualizer.graphs.utils.buildPathFromPreviousNodeMapping


@Suppress("UNCHECKED_CAST")
fun <T, N : Number> dijkstra(
    graph: WeightedGraph<T, N>,
    startNode: Node<T>,
    endNode: Node<T>?,
    numberAdapter: NumberAdapter<N>,
    onNodeVisited: (Node<T>) -> Unit = {}
): Map<Node<T>, WeightedEdge<T, N>> {
    //as dijkstra is practically a special a*, i'll reuse that code
    //dijkstra's heuristic is always 0
    return aStar(
        graph,
        startNode,
        endNode,
        { 0.0 },
        numberAdapter,
        onNodeVisited
    )
}

fun <T, N : Number> dijkstraShortestPathOrNull(
    graph: WeightedGraph<T, N>,
    startNode: Node<T>,
    endNode: Node<T>,
    numberAdapter: NumberAdapter<N>,
    onNodeVisited: (Node<T>) -> Unit = {}
): WeightedGraph<T, N>? {
    val previousNodeMapping = dijkstra(graph, startNode, endNode, numberAdapter, onNodeVisited)
    return if (previousNodeMapping.containsKey(endNode))
        buildPathFromPreviousNodeMapping(endNode, previousNodeMapping)
    else null
}
