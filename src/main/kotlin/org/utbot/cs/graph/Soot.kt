package org.utbot.cs.graph

import soot.SootMethod
import soot.jimple.Stmt
import soot.toolkits.graph.ExceptionalUnitGraph

class SootGraph(
    val method: SootMethod
) : Graph<Stmt> {
    private val graph = ExceptionalUnitGraph(method.activeBody)
    override fun allVertices(): List<Stmt> {
        return graph.body.units.map { it as Stmt }
    }

    override fun getSuccs(vertex: Stmt): List<Stmt> {
        return graph.getUnexceptionalSuccsOf(vertex).map { it as Stmt }
    }
}