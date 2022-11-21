package org.utbot.cs.core.state

import org.utbot.cs.core.domain.SymbolicValue
import org.utbot.cs.core.symbolic.Memory
import org.utbot.cs.core.symbolic.SymbolicConstraintsUpdate
import org.utbot.cs.core.symbolic.SymbolicState
import org.utbot.cs.core.symbolic.SymbolicStateUpdate
import org.utbot.cs.graph.Node
import org.utbot.cs.graph.Path
import soot.SootMethod
import soot.jimple.Stmt
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

data class Parameter(
    val id: LocalId,
    val symbolicValue: SymbolicValue,
)

data class StackElement(
    val callerNode: Node?,
    val parameters: PersistentList<Parameter> = persistentListOf(),
    val localToUpdate: LocalId? = null
)

//interface ExecutionState<T : ExecutionState<T, U>, U : StateUpdate> : State<T, U> {
//    val executionStack: List<StackElement>
//    val symbolicState
//}

data class ExecutionState(
    val path: Path,
    val executionStack: PersistentList<StackElement> = persistentListOf(StackElement(null)),
    val symbolicState: SymbolicState = SymbolicState(persistentListOf(), Memory()),
    val timesMerged: Int = 0,
    val methodToCallTimes: PersistentMap<SootMethod, Int> = persistentMapOf(),
) {
    val topStmt: Stmt get() = path.last().stmt
    val topMethod: SootMethod get() = path.last().method
}

class ExecutionStateUpdate(
    val update: InternalExecutionStateUpdate = NopUpdate,
    val symbolicUpdates: Collection<SymbolicStateUpdate> = emptyList()
) : StateUpdate

sealed interface InternalExecutionStateUpdate

data class ParameterDeclaration(
    val id: LocalId,
) : InternalExecutionStateUpdate

object NopUpdate : InternalExecutionStateUpdate

data class RetUpdate(
    val returnValue: SymbolicValue,
) : InternalExecutionStateUpdate

data class MethodCall(
    val calleeStmt: Stmt,
    val parameters: List<SymbolicValue>,
    val localToUpdate: LocalId?,
) : InternalExecutionStateUpdate

data class IfStmt(
    val condition: SymbolicConstraintsUpdate,
    val negCondition: SymbolicConstraintsUpdate,
    val target: Stmt,
) : InternalExecutionStateUpdate


data class SwitchTarget(
    val condition: SymbolicConstraintsUpdate,
    val target: Stmt
)
data class SwitchUpdate(
    val targets: List<SwitchTarget>
) : InternalExecutionStateUpdate

//class AssignmentUpdate : ExecutionStateUpdate
//
//class IdentityUpdate : ExecutionStateUpdate
//
//class IfUpdate : ExecutionStateUpdate
//
//class InvokeUpdate : ExecutionStateUpdate
//
//class SwitchUpdate : ExecutionStateUpdate
//
//class ReturnUpdate : ExecutionStateUpdate
//
//class ReturnVoidUpdate : ExecutionStateUpdate
//
//class ThrowUpdate : ExecutionStateUpdate
//
//class PassUpdate : ExecutionStateUpdate