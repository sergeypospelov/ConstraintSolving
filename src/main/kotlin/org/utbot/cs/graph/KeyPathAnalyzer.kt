package org.utbot.cs.graph


interface Graph<T> {
    fun allVertices(): List<T>
    fun getSuccs(vertex: T): List<T>
}

class KeyPathAnalyzer<T>(private val graph: Graph<T>, private val keyPath: List<T>) {
    private val visited = mutableSetOf<T>()
    private val stmtToOrder = mutableMapOf<T, Int>()

    private fun dfs(stmt: T) {
        visited += stmt
        for (succ in graph.getSuccs(stmt)) {
            if (succ !in visited) {
                dfs(succ)
            }
        }
        stmtToOrder[stmt] = graph.allVertices().size - stmtToOrder.size - 1
    }

    fun analyze(): Graph<T> {
        dfs(graph.allVertices().first())

        val allowedEdges = graph.allVertices().associateWith { mutableListOf<T>() }

        val visited = mutableSetOf<T>()
        val ok = mutableSetOf<T>()
        fun dfsInner(u: T, targetVertex: T) {
            visited += u
            for (succ in graph.getSuccs(u)) {
                if (stmtToOrder.getValue(succ) > stmtToOrder.getValue(targetVertex)) {
                    continue
                }

                if (succ !in visited) {
                    dfsInner(succ, targetVertex)
                }

                if (succ in ok) {
                    allowedEdges.getValue(u) += succ
                    if (u !in ok) {
                        ok += u
                    }
                }
            }
        }

        var cur = graph.allVertices().first()
        for (keyStmt in keyPath) {
            ok += keyStmt
            dfsInner(cur, keyStmt)

            cur = keyStmt

        }

        return object : Graph<T> {
            override fun allVertices(): List<T> =
                graph.allVertices()


            override fun getSuccs(vertex: T): List<T> =
                allowedEdges.getValue(vertex)
        }
    }
}