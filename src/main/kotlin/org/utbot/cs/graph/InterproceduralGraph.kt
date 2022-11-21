package org.utbot.cs.graph

import soot.SootMethod
import soot.jimple.Stmt
import soot.toolkits.graph.ExceptionalUnitGraph

data class MethodInfo(
    val method: SootMethod,
    val stmts: List<Stmt>,
)

class Node constructor(
    val callId: Int,
    val method: SootMethod,
    val stmt: Stmt,
) {
    override fun toString(): String {
        return "Node(${method.name}:$callId:$stmt)"
    }
}


class InterProceduralGraph<T : Any>(
    private val taintPath: List<Statement>,
) {
    private val methodToKeyStatements = taintPath.groupBy { it.method }
    private val stmtToStatement = taintPath.associateBy { it.stmt }

    private var callCounter: Int = 0

    class IntraProceduralGraph<T>(
        val sootMethod: SootMethod,
        val callId: Int,
        val graph: Graph<Stmt>,
    ) {
        private val stmtToData = mutableMapOf<Stmt, T>()
        private val stmtToNode = mutableMapOf<Stmt, Node>()

        inner class NodeEntry(
            private val stmt: Stmt,
        ) {
            fun succs(): List<NodeEntry> {
                val stmts = graph.getSuccs(stmt)
                return stmts.map { NodeEntry(it) }
            }

            fun node(): Node =
                stmtToNode.getOrPut(stmt) { Node(callId, sootMethod, stmt) }

            fun data(): T? =
                stmtToData[stmt]

            fun putData(value: T) =
                stmtToData.put(stmt, value)
        }

        fun firstNodeEntry(): NodeEntry {
            return NodeEntry(graph.allVertices().first())
        }

        operator fun get(stmt: Stmt): NodeEntry = NodeEntry(stmt)
    }

    private val callIdToGraph = mutableMapOf<Int, IntraProceduralGraph<T>>()

    fun newMethodCall(method: SootMethod): IntraProceduralGraph<T>.NodeEntry {
        val callId = callCounter++

        var graph: Graph<Stmt> = SootGraph(method)

        if (method in methodToKeyStatements) {
            val keyStmts = methodToKeyStatements.getValue(method).map { it.stmt }
            val analyzer = KeyPathAnalyzer(graph, keyStmts)
            graph = analyzer.analyze()
        }

        val intraProceduralGraph = IntraProceduralGraph<T>(method, callId, graph)

        val entry = intraProceduralGraph.firstNodeEntry()
        callIdToGraph[callId] = intraProceduralGraph

        return entry
    }

    fun unbalancedReturn(stmt: Stmt): IntraProceduralGraph<T>.NodeEntry {
        val returnStmt = requireNotNull((stmtToStatement.get(stmt) as? ReturnStmt)) {
            "Can't obtain return statement for unbalanced return"
        }
        val callStmt = requireNotNull(returnStmt.callSite) {
            "Can't obtain call site for unbalanced return"
        }
        val callMethod = requireNotNull(returnStmt.callMethod) {
            "Can't obtain call method for unbalanced return"
        }


        val callId = callCounter++

        val keyStmts = requireNotNull(methodToKeyStatements[callMethod]?.map { it.stmt }) {
            "Can't find key stms for unbalanced return"
        }

        var graph: Graph<Stmt> = SootGraph(callMethod)

        val analyzer = KeyPathAnalyzer(graph, keyStmts)
        graph = analyzer.analyze()

        val intraProceduralGraph = IntraProceduralGraph<T>(callMethod, callId, graph)

        val entry = intraProceduralGraph[callStmt]
        callIdToGraph[callId] = intraProceduralGraph

        return entry
    }

    operator fun get(node: Node): IntraProceduralGraph<T>.NodeEntry =
        callIdToGraph[node.callId]?.get(node.stmt) ?: error("")

}

