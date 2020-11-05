package hu.bme.aut.android.pathfindingvisualizer.model.graphs.utils

import hu.bme.aut.android.pathfindingvisualizer.model.graphs.MutableNode
import hu.bme.aut.android.pathfindingvisualizer.model.graphs.Node
import hu.bme.aut.android.pathfindingvisualizer.model.graphs.SimpleMutableNode
import hu.bme.aut.android.pathfindingvisualizer.model.graphs.SimpleNode

val <T> MutableNode<T>.immutable: Node<T>
    get() {
        return SimpleNode(
            value
        )
    }

val <T> Node<T>.mutable: MutableNode<T>
    get() {
        return SimpleMutableNode(
            value
        )
    }


val <T> T.mutableNode: MutableNode<T>
    get() {
        return SimpleMutableNode(
            this
        )
    }

val <T> T.node: Node<T>
    get() {
        return SimpleNode(
            this
        )
    }

