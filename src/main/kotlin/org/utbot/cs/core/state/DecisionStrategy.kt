package org.utbot.cs.core.state

import soot.jimple.internal.JIfStmt

interface DecisionStrategy {
    fun ifStmt(stmt: JIfStmt): Boolean
}

class IfStmtDecisionStrategy(
    val go: Boolean
) : DecisionStrategy {
    override fun ifStmt(stmt: JIfStmt) = go
}
