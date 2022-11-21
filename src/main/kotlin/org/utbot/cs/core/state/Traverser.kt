package org.utbot.cs.core.state

import org.utbot.cs.core.domain.PrimitiveValue
import org.utbot.cs.core.domain.SymbolicValue
import org.utbot.cs.core.domain.toValue
import org.utbot.cs.core.svm.SymbolicVirtualMachine
import org.utbot.cs.core.svm.TypeRegistry
import org.utbot.cs.core.symbolic.LocalUpdate
import org.utbot.cs.core.symbolic.SymbolicConstraintsUpdate
import org.utbot.cs.core.symbolic.SymbolicMemoryUpdate
import org.utbot.cs.core.symbolic.SymbolicStateUpdate
import org.utbot.cs.graph.InterProceduralGraph
import org.utbot.cs.graph.Path
import org.utbot.cs.soot.util.JimpleStmtVisitor
import org.utbot.cs.soot.util.visitJimpleStmt
import org.ksmt.KContext
import org.ksmt.utils.asExpr
import soot.SootMethod
import soot.jimple.AssignStmt
import soot.jimple.IdentityStmt
import soot.jimple.InvokeExpr
import soot.jimple.MonitorStmt
import soot.jimple.Stmt
import soot.jimple.SwitchStmt
import soot.jimple.internal.JAssignStmt
import soot.jimple.internal.JBreakpointStmt
import soot.jimple.internal.JDynamicInvokeExpr
import soot.jimple.internal.JGotoStmt
import soot.jimple.internal.JIdentityStmt
import soot.jimple.internal.JIfStmt
import soot.jimple.internal.JInterfaceInvokeExpr
import soot.jimple.internal.JInvokeStmt
import soot.jimple.internal.JLookupSwitchStmt
import soot.jimple.internal.JNopStmt
import soot.jimple.internal.JReturnStmt
import soot.jimple.internal.JReturnVoidStmt
import soot.jimple.internal.JSpecialInvokeExpr
import soot.jimple.internal.JStaticInvokeExpr
import soot.jimple.internal.JTableSwitchStmt
import soot.jimple.internal.JThrowStmt
import soot.jimple.internal.JVirtualInvokeExpr
import soot.jimple.internal.JimpleLocal
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf

class Traverser(
    private val graph: InterProceduralGraph<ExecutionState>,
    private val kContext: KContext,
    private val typeRegistry: TypeRegistry,
    val mockingStrategy: MockingStrategy,
) {
    fun traverse(executionState: ExecutionState): List<ExecutionState> {
        val svm = SymbolicVirtualMachine(kContext, executionState.symbolicState, typeRegistry)
        val valueResolvingStrategy = BaseValueResolvingStrategy(executionState.topMethod, svm)

        val stmt = executionState.topStmt

        val visitor = StmtVisitor(svm, valueResolvingStrategy)

        val update = visitJimpleStmt(stmt, visitor)
        val symbolicUpdates = svm.collectUpdates()

        val states = applyUpdates(svm, executionState, update, symbolicUpdates)
        return states
    }

    private fun applyUpdates(
        svm: SymbolicVirtualMachine,
        executionState: ExecutionState,
        update: InternalExecutionStateUpdate,
        symbolicUpdates: List<SymbolicStateUpdate>,
    ): List<ExecutionState> =
        when (update) {
            is IfStmt -> traverseIfStmt(executionState, symbolicUpdates, update)

            is MethodCall -> traverseMethodCall(executionState, update, svm, symbolicUpdates)

            NopUpdate -> traverseNopUpdate(executionState, symbolicUpdates)

            is ParameterDeclaration -> traverseParameterDeclaration(executionState, symbolicUpdates, update)

            is RetUpdate -> traverseRetUpdate(executionState, symbolicUpdates, svm, update)

            is SwitchUpdate -> traverseSwitchStmt(executionState, update, symbolicUpdates)
        }

    private fun traverseIfStmt(
        executionState: ExecutionState,
        symbolicUpdates: List<SymbolicStateUpdate>,
        update: IfStmt,
    ) = with(executionState) {
            val nodes = graph[path.last()].succs().map { it.node() }

            nodes.map { node ->

                val symbolicUpdate = if (node.stmt == update.target) {
                    update.condition
                } else {
                    update.negCondition
                }


                val posSymbolicState = symbolicState.multiUpdate(symbolicUpdates + symbolicUpdate)
                copy(path = Path(path + node), symbolicState = posSymbolicState)
            }

        }

    private fun traverseMethodCall(
        executionState: ExecutionState,
        update: MethodCall,
        svm: SymbolicVirtualMachine,
        symbolicUpdates: List<SymbolicStateUpdate>,
    ): List<ExecutionState> {
        val newState = with(executionState) {
            val method = update.calleeStmt.invokeExpr.method

            val localId = ((update.calleeStmt as? AssignStmt)?.leftOp as? JimpleLocal)?.run {
                LocalId(
                    topMethod,
                    name,
                    type
                )
            }

            if (mockingStrategy.mustMock(executionState, graph, method)) {
                val nextNode = graph[path.last()].succs().first().node()


                if (localId != null) {
                    val symbolicUpdate =
                        SymbolicMemoryUpdate(LocalUpdate(localId, svm.createConst(localId.type)))
                    val newSymbolicState = symbolicState.multiUpdate(svm.collectUpdates() + symbolicUpdate)


                    copy(path = Path(path + nextNode), symbolicState = newSymbolicState)
                } else {
                    copy(path = Path(path + nextNode))
                }

            } else {
                val nextNode = graph.newMethodCall(method).node()


                val newMethodToCall =
                    methodToCallTimes.put(method, methodToCallTimes.getOrDefault(method, 0) + 1)

                val parameterValues = update.parameters
                var locals = LocalsStorage()

                method.activeBody.units
                    .filterIsInstance<IdentityStmt>()
                    .mapIndexed { idx, stmt ->
                        val local = stmt.leftOp as JimpleLocal
                        locals = locals.set(
                            LocalId(method, local.name, local.type),
                            parameterValues.getOrNull(idx) ?: svm.createConst(local.type)
                        )
                    }


                val stackElement = StackElement(callerNode = path.last(), localToUpdate = localId)
                val symbolicState = symbolicState.multiUpdate(symbolicUpdates).copy(
                    memory = symbolicState.memory.copy(
                        locals = LocalsStorage(
                            symbolicState.memory.locals.locals.putAll(locals.locals)
                        )
                    )
                )

                val newExecutionStack = executionStack.add(stackElement)
                copy(
                    path = Path(path + nextNode),
                    symbolicState = symbolicState,
                    executionStack = newExecutionStack,
                    methodToCallTimes = newMethodToCall
                )
            }
        }
        return listOf(newState)
    }

    private fun traverseNopUpdate(
        executionState: ExecutionState,
        symbolicUpdates: List<SymbolicStateUpdate>,
    ): List<ExecutionState> {
        val newState = with(executionState) {
            graph[path.last()].succs().map {
                val nextNode = it.node()

                val newSymbolicState = symbolicState.multiUpdate(symbolicUpdates)

                copy(
                    path = Path(path + nextNode),
                    symbolicState = newSymbolicState
                )
            }
        }
        return newState
    }

    private fun traverseParameterDeclaration(
        executionState: ExecutionState,
        symbolicUpdates: List<SymbolicStateUpdate>,
        update: ParameterDeclaration,
    ): List<ExecutionState> {
        val newState = with(executionState) {
            val nextNode = graph[path.last()].succs().first().node()
            val newSymbolicState = symbolicState.multiUpdate(symbolicUpdates)

            val parameter = Parameter(update.id, newSymbolicState.memory.findLocal(update.id)!!)
            val newParameters = executionStack.last().parameters.add(parameter)
            val newStackElement = executionStack.last().copy(parameters = newParameters)
            val newExecutionStack = executionStack.set(executionStack.size - 1, newStackElement)


            copy(
                path = Path(path + nextNode),
                executionStack = newExecutionStack, symbolicState = newSymbolicState
            )
        }
        return listOf(newState)
    }

    private fun traverseRetUpdate(
        executionState: ExecutionState,
        symbolicUpdates: List<SymbolicStateUpdate>,
        svm: SymbolicVirtualMachine,
        update: RetUpdate,
    ): List<ExecutionState> {
        val newState = with(executionState) {
            if (executionStack.size == 1) {
                val nextNode = graph.unbalancedReturn(executionState.topStmt).node()

                val newExecutionStack = executionStack.set(0, StackElement(null, persistentListOf(), null))
                var newLocals = LocalsStorage(
                    symbolicState.memory.locals.locals.mutate { it.keys.removeIf { localId -> localId.sootMethod == topMethod } }
                )

                val method = nextNode.method
                method.activeBody.locals
                   .map { local ->
                        newLocals = newLocals.set(
                            LocalId(method, local.name, local.type),
                            svm.createConst(local.type)
                        )
                    }


                val newSymbolicState = symbolicState.multiUpdate(symbolicUpdates).copy(
                    memory = symbolicState.memory.copy(
                        locals = newLocals
                    )
                )

                copy(
                    path = Path(path + nextNode),
                    executionStack = newExecutionStack,

                    symbolicState = newSymbolicState
                )
            } else {
                val nextNode = graph[executionStack.last().callerNode!!].succs().first().node()

                val stackElement = executionStack.last()

                val localId = stackElement.localToUpdate
                val updates = symbolicUpdates + if (localId != null) listOf(
                    SymbolicMemoryUpdate(
                        LocalUpdate(
                            localId,
                            update.returnValue
                        )
                    )
                ) else emptyList()
                val newExecutionStack = executionStack.removeAt(executionStack.size - 1)
                val newLocals =
                    symbolicState.memory.locals.locals.mutate { it.keys.removeIf { localId -> localId.sootMethod == topMethod } }

                val newSymbolicState = symbolicState.copy(
                    memory = symbolicState.memory.copy(
                        locals = LocalsStorage(newLocals)
                    )
                ).multiUpdate(updates)

                copy(
                    path = Path(path + nextNode),
                    executionStack = newExecutionStack,
                    symbolicState = newSymbolicState
                )
            }
        }
        return listOf(newState)
    }

    private fun traverseSwitchStmt(
        executionState: ExecutionState,
        update: SwitchUpdate,
        symbolicUpdates: List<SymbolicStateUpdate>,
    ) =
        with(executionState) {
            val stmtToConstraints = update.targets.associateBy({ it.target }) { it.condition }
            graph[path.last()].succs().map {
                val node = it.node()
                val constraint = stmtToConstraints.getValue(node.stmt)

                val newSymbolicState = symbolicState.multiUpdate(symbolicUpdates + constraint)
                copy(
                    path = Path(path + node),
                    symbolicState = newSymbolicState
                )
            }
        }
}

class MockingStrategy {
    fun mustMock(
        executionState: ExecutionState,
        graph: InterProceduralGraph<ExecutionState>,
        method: SootMethod,
    ): Boolean {
        val tooManyTimes = executionState.methodToCallTimes.getOrDefault(method, 0) >= 4
        val libraryOrDependencyMethod = method.declaringClass.isPhantomClass || method.declaringClass.isLibraryClass || method.isJavaLibraryMethod

        return tooManyTimes || libraryOrDependencyMethod || !method.hasActiveBody()
    }
}

class StmtVisitor(
    val svm: SymbolicVirtualMachine,
    val valueResolvingStrategy: BaseValueResolvingStrategy,
) : JimpleStmtVisitor<InternalExecutionStateUpdate> {
    override fun visit(stmt: JAssignStmt): InternalExecutionStateUpdate {
        val left = valueResolvingStrategy.resolveLValue(stmt.leftOp)
        val rightValue = stmt.rightOp
        val update = if (rightValue is InvokeExpr) {
            val argumentValues = collectArgumentValues(rightValue)
            val localId = checkNotNull((left as? LocalRefLValue)?.localId) { "Require a local for InvokeExpr" }
            MethodCall(stmt, argumentValues, localId)
        } else {
            val right = valueResolvingStrategy.resolveValue(rightValue)
            left.update(svm, right)
            NopUpdate
        }

        return update
    }

    override fun visit(stmt: JIdentityStmt): InternalExecutionStateUpdate {
        val local = requireNotNull(stmt.leftOp as? JimpleLocal) {
            "Expected JimpleLocal, but got: ${stmt.leftOp::class.java}"
        }
        val localId = LocalId(valueResolvingStrategy.sootMethod, local.name, local.type)
        valueResolvingStrategy.resolveLocal(stmt.leftOp as JimpleLocal) ?: svm.createLocal(localId)

        return ParameterDeclaration(localId)
    }

    override fun visit(stmt: JIfStmt): InternalExecutionStateUpdate {
        val conditionValue = valueResolvingStrategy.resolveValue(stmt.condition)
        val condition = (conditionValue as PrimitiveValue).expr.asExpr(svm.kContext.mkBoolSort())
        val negCondition = svm.doNegate(conditionValue).expr.asExpr(svm.kContext.mkBoolSort())

        return IfStmt(SymbolicConstraintsUpdate(condition), SymbolicConstraintsUpdate(negCondition), stmt.target)
    }

    override fun visit(stmt: JInvokeStmt): InternalExecutionStateUpdate {
        val argumentValues = collectArgumentValues(stmt.invokeExpr)
        return MethodCall(stmt, argumentValues, localToUpdate = null)
    }

    override fun visit(stmt: SwitchStmt): InternalExecutionStateUpdate {
        val value = valueResolvingStrategy.resolveValue(stmt.key) as PrimitiveValue
        val switch = when (stmt) {
            is JTableSwitchStmt -> {
                val targets = (stmt.lowIndex..stmt.highIndex).mapIndexed { idx, branchValue ->
                    val eqConstraint = svm.doEq(
                        value,
                        svm.intValue(branchValue)
                    )
                    val update = SymbolicConstraintsUpdate(eqConstraint)
                    SwitchTarget(update, stmt.getTarget(idx) as Stmt)
                }

                val lowIndex = svm.intValue(stmt.lowIndex)
                val highIndex = svm.intValue(stmt.highIndex)
                val orConstraint = svm.doOr(svm.doLt(lowIndex, value).toValue(), svm.doGt(highIndex, value).toValue())
                val update = SymbolicConstraintsUpdate(orConstraint)
                val default = SwitchTarget(update, stmt.defaultTarget as Stmt)

                SwitchUpdate(targets + default)
            }

            is JLookupSwitchStmt -> {
                val targets = stmt.lookupValues.mapIndexed { idx, branchValue ->
                    val eqConstraint = svm.doEq(value, svm.intValue(branchValue.value))
                    val update = SymbolicConstraintsUpdate(eqConstraint)
                    SwitchTarget(update, stmt.getTarget(idx) as Stmt)
                }

                val notEqConstraints = stmt.lookupValues.map { branchValue ->
                    svm.doNegate(
                        svm.doEq(value, svm.intValue(branchValue.value)).toValue()
                    ).expr.asExpr(svm.kContext.boolSort)
                }
                val andConstraint = svm.kContext.mkAnd(notEqConstraints)
                val update = SymbolicConstraintsUpdate(andConstraint)
                val default = SwitchTarget(update, stmt.defaultTarget as Stmt)

                SwitchUpdate(targets + default)
            }

            else -> error("Unknown switch: $stmt")
        }
        return switch
    }

    override fun visit(stmt: JReturnStmt): InternalExecutionStateUpdate =
        RetUpdate(valueResolvingStrategy.resolveValue(stmt.op))

    override fun visit(stmt: JReturnVoidStmt): InternalExecutionStateUpdate =
        RetUpdate(svm.voidValue())

    override fun visit(stmt: JThrowStmt): InternalExecutionStateUpdate {
        return NopUpdate
    }

    override fun visit(stmt: JBreakpointStmt): InternalExecutionStateUpdate {
        return NopUpdate
    }

    override fun visit(stmt: JGotoStmt): InternalExecutionStateUpdate {
        return NopUpdate
    }

    override fun visit(stmt: JNopStmt): InternalExecutionStateUpdate {
        return NopUpdate
    }

    override fun visit(stmt: MonitorStmt): InternalExecutionStateUpdate {
        return NopUpdate
    }


    // private

    private fun collectArgumentValues(invokeExpr: InvokeExpr): List<SymbolicValue> =
        when (invokeExpr) {
            is JVirtualInvokeExpr -> collectValuesForVirtualInvoke(invokeExpr)
            is JSpecialInvokeExpr -> collectValuesForSpecialInvoke(invokeExpr)
            is JDynamicInvokeExpr -> collectValuesForDynamicInvoke(invokeExpr)
            is JStaticInvokeExpr -> collectValuesForStaticInvoke(invokeExpr)
            is JInterfaceInvokeExpr -> collectValuesForInterfaceInvoke(invokeExpr)
            else -> error("Unknown invoke: ${invokeExpr::class.java}")
        }

    private fun collectValuesForSpecialInvoke(invokeExpr: JSpecialInvokeExpr): List<SymbolicValue> {
        val base = valueResolvingStrategy.resolveValue(invokeExpr.base)
        val arguments = invokeExpr.args.map { valueResolvingStrategy.resolveValue(it) }

        return listOf(base) + arguments
    }

    private fun collectValuesForDynamicInvoke(invokeExpr: JDynamicInvokeExpr): List<SymbolicValue> {
        val arguments = invokeExpr.args.map { valueResolvingStrategy.resolveValue(it) }

        return arguments
    }

    private fun collectValuesForInterfaceInvoke(invokeExpr: JInterfaceInvokeExpr): List<SymbolicValue> {
        val base = valueResolvingStrategy.resolveValue(invokeExpr.base)
        val arguments = invokeExpr.args.map { valueResolvingStrategy.resolveValue(it) }

        return listOf(base) + arguments
    }

    private fun collectValuesForStaticInvoke(invokeExpr: JStaticInvokeExpr): List<SymbolicValue> {
        val arguments = invokeExpr.args.map { valueResolvingStrategy.resolveValue(it) }

        return arguments
    }

    private fun collectValuesForVirtualInvoke(invokeExpr: JVirtualInvokeExpr): List<SymbolicValue> {
        val base = valueResolvingStrategy.resolveValue(invokeExpr.base)
        val arguments = invokeExpr.args.map { valueResolvingStrategy.resolveValue(it) }

        return listOf(base) + arguments
    }
}