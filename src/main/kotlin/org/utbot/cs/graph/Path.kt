package org.utbot.cs.graph

import soot.SootMethod
import soot.jimple.Stmt
import kotlinx.collections.immutable.PersistentList

class Path(
    val path: List<Node>
) : List<Node> by path


//enum class SuccessorType {
//    DEFAULT,
//    METHOD_CALL,
//    RETURN,
//    IN_OUT_METHOD_CALL,
//    UNBALANCED_RETURN,
//}
//
//interface Path {
//    interface Successor {
//        fun node(): Node
//        fun type(): SuccessorType
//        fun prolong(): Path
//    }
//
//    fun getSuccs(): List<Successor>
//}
//
//class DefaultPath(
//    private val graph: InterProceduralGraph<*>,
//    private val stmts: PersistentList<Node>,
//) : Path {
//    private inner class Successor(
//        val node: Node,
//    ) : Path.Successor {
//        override fun node(): Node = node
//
//        override fun type(): SuccessorType = SuccessorType.DEFAULT
//
//        override fun prolong(): DefaultPath = DefaultPath(graph, stmts.add(node))
//    }
//
//    override fun getSuccs(): List<Path.Successor> {
//        return graph[stmts.last()].succs().map { Successor(it.node()) }
//    }
//}


