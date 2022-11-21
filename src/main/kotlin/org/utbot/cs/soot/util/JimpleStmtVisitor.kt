package org.utbot.cs.soot.util

import soot.jimple.MonitorStmt
import soot.jimple.Stmt
import soot.jimple.SwitchStmt
import soot.jimple.internal.*

interface JimpleStmtVisitor<T> {
    fun visit(stmt: JAssignStmt): T
    fun visit(stmt: JIdentityStmt): T
    fun visit(stmt: JIfStmt): T
    fun visit(stmt: JInvokeStmt): T
    fun visit(stmt: SwitchStmt): T
    fun visit(stmt: JReturnStmt): T
    fun visit(stmt: JReturnVoidStmt): T
    fun visit(stmt: JThrowStmt): T
    fun visit(stmt: JBreakpointStmt): T
    fun visit(stmt: JGotoStmt): T
    fun visit(stmt: JNopStmt): T
    fun visit(stmt: MonitorStmt): T
}

fun <T> visitJimpleStmt(stmt: Stmt, visitor: JimpleStmtVisitor<T>): T =
    when (stmt) {
        is JAssignStmt -> visitor.visit(stmt)
        is JIdentityStmt -> visitor.visit(stmt)
        is JIfStmt -> visitor.visit(stmt)
        is JInvokeStmt -> visitor.visit(stmt)
        is SwitchStmt -> visitor.visit(stmt)
        is JReturnStmt -> visitor.visit(stmt)
        is JReturnVoidStmt -> visitor.visit(stmt)
        is JThrowStmt -> visitor.visit(stmt)
        is JBreakpointStmt -> visitor.visit(stmt)
        is JGotoStmt -> visitor.visit(stmt)
        is JNopStmt -> visitor.visit(stmt)
        is MonitorStmt -> visitor.visit(stmt)
        else -> error("Unsupported: ${stmt::class}")
    }