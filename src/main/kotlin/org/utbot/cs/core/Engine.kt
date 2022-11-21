package org.utbot.cs.core

import org.utbot.cs.core.domain.PrimitiveValue
import org.utbot.cs.core.domain.ReferenceValue
import org.utbot.cs.core.domain.SymbolicValue
import org.utbot.cs.core.state.ExecutionState
import org.utbot.cs.core.state.ExecutionStateMerger
import org.utbot.cs.core.state.MockingStrategy
import org.utbot.cs.core.state.Traverser
import org.utbot.cs.core.svm.SymbolicVirtualMachine
import org.utbot.cs.core.svm.TypeRegistry
import org.utbot.cs.core.util.value
import org.utbot.cs.graph.InterProceduralGraph
import org.utbot.cs.graph.Node
import org.utbot.cs.graph.Path
import org.utbot.cs.graph.Statement
import org.ksmt.KContext
import org.ksmt.solver.KModel
import org.ksmt.solver.KSolverStatus
import org.ksmt.solver.z3.KZ3Solver
import soot.RefType
import soot.SootClass
import soot.SootField
import soot.SootMethod
import java.io.Closeable

data class Result(
    val result: Boolean,
    val failed: Boolean
)

class Engine : Closeable {
    private val kContext = KContext()
    private val typeRegistry = TypeRegistry(kContext)

    fun analyze(method: SootMethod, taintPath: List<Statement>): Result {
        try {
            val graph = InterProceduralGraph<ExecutionState>(taintPath)

            val firstEntry = graph.newMethodCall(method)
            val firstNode = firstEntry.node()
            val path = Path(listOf(firstNode))
            val firstState = ExecutionState(path,)
            firstEntry.putData(firstState)

            val nodesQueue = ArrayDeque(listOf(firstNode))
            val terminalStates = mutableListOf<ExecutionState>()
            val processed = mutableSetOf<Node>()
            var statesProcessed = 0

            while (nodesQueue.isNotEmpty()) {
                statesProcessed++

                val node = nodesQueue.removeFirst()

                if (node in processed) {
                    continue
                }
                processed += node

                val executionState = graph[node].data() ?: error("Can't find state associated with $node")

                if (isTerminal(executionState, taintPath)) {
                    return Result(result = check(executionState), failed = false)

                }

                val traverser = Traverser(graph, kContext, typeRegistry, MockingStrategy())
                val states = traverser.traverse(executionState)

                for (state in states) {
                    val previousState = graph[state.path.last()].data()

                    val newState = if (previousState == null) {
                        state
                    } else {
                        merge(state, previousState)
                    }
                    val newNode = newState.path.last()
                    graph[newNode].putData(newState)

                    if (newNode !in processed) {
                        if (newNode.stmt != graph[node].succs().firstOrNull()?.node()?.stmt) {
                            nodesQueue.addLast(newNode)
                        } else {
                            nodesQueue.addFirst(newNode)
                        }
                    }
                }
            }

            return Result(result = false, failed = false)
        } catch (e: Throwable) {
//            throw e
            return Result(result = true, failed = true)
        }
    }

    private fun isTerminal(executionState: ExecutionState, stmts: List<Statement>): Boolean {
        return stmts.last().stmt == executionState.topStmt
    }

    private fun shouldAdd(
        graph: InterProceduralGraph<ExecutionState>,
        newState: ExecutionState,
    ): Boolean {
        return when {
            true -> true
            else -> false
        }
    }

    private fun merge(
        newState: ExecutionState,
        previousState: ExecutionState,
    ): ExecutionState {
        val svm = SymbolicVirtualMachine(kContext, newState.symbolicState, typeRegistry)

        val merger = ExecutionStateMerger(svm)
        val mergedState = merger.merge(previousState, newState)
        return mergedState
    }

    sealed interface Model

    data class Primitive(
        val primitive: Any,
    ) : Model

    data class Composite(
        val addr: Int,
        val clss: SootClass,
        val fields: Map<SootField, Model>,
    ) : Model {
        override fun toString(): String {
            return buildString {
                append("${clss}: $addr")
                append("[")
                fields.forEach {
                    val inner = it.value.toString()
                    append("${it.key}: $inner, ")
                }
                append("]")
            }
        }
    }

    private fun resolve(model: KModel, sym: SymbolicValue): Model =
        when (sym) {
            is PrimitiveValue -> Primitive(model.eval(sym.expr, true).value())
            is ReferenceValue -> {
                val addr = model.eval(sym.addr, true)
                val symType = sym.type as RefType
                val fields = symType.sootClass.fields
//                val fieldModels = fields
//                    .map { ChunkDescriptor(symType, it) }
//                    .filter { memory.fieldsState.contains(it) }
//                    .map { ctx.mkArraySelect(memory.findArray(ctx, it), addr) }
//                    .map { resolve(model, ctx, memory, SymbolicValue()) }
//            }
                Composite(addr.value() as Int, symType.sootClass, emptyMap())
            }
        }

    private fun check(executionState: ExecutionState): Boolean =
        with(KZ3Solver(kContext)) {
            use {
                executionState.symbolicState.constraints.forEach(::assert)
                val res = check()
                if (res == KSolverStatus.SAT) {
                    val model = model()
//
//                    run {
//                        val lastStackElement = executionState.executionStack.last()
//                        lastStackElement.parameters.forEach {
//                            val sym = it.symbolicValue
//                            val res = resolve(model, sym)
//                            println(res)
//                        }
//                    }
//
                    true
                } else {
                    false
                }
            }
        }

    override fun close() {
        kContext.close()
    }
}