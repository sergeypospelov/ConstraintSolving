package org.utbot.cs.graph

import soot.SootMethod
import soot.jimple.Stmt

interface Statement {
    val stmt: Stmt
    val method: SootMethod
}

data class AssignStmt(override val stmt: Stmt, override val method: SootMethod) : Statement

data class EntryPointStmt(override val stmt: Stmt, override val method: SootMethod) : Statement

data class InCallStmt(override val stmt: Stmt, override val method: SootMethod) : Statement

data class InOutCallStmt(override val stmt: Stmt, override val method: SootMethod) : Statement

data class OutCallStmt(override val stmt: Stmt, override val method: SootMethod) : Statement

data class ReadStmt(override val stmt: Stmt, override val method: SootMethod) : Statement

data class ReturnStmt(override val stmt: Stmt, override val method: SootMethod, val callSite: Stmt?, val callMethod: SootMethod?) : Statement

data class SinkStmt(override val stmt: Stmt, override val method: SootMethod) : Statement
