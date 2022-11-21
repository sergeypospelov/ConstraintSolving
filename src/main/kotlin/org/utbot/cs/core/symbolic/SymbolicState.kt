package org.utbot.cs.core.symbolic

import org.utbot.cs.core.domain.BoolExpr
import org.utbot.cs.core.state.State
import org.utbot.cs.core.state.StateUpdate
import org.ksmt.expr.KExpr
import org.ksmt.sort.KBoolSort
import kotlinx.collections.immutable.PersistentList


data class SymbolicState constructor(
    val constraints: PersistentList<BoolExpr>,
    val memory: Memory,
    private var memoryPtr: Int = 1,
) : State<SymbolicState, SymbolicStateUpdate> {

    fun findNewAddr(): Int {
        return memoryPtr++
    }

    override fun update(update: SymbolicStateUpdate): SymbolicState =
        when (update) {
            is SymbolicConstraintsUpdate -> copy(constraints = constraints.addAll(update.constraints))
            is SymbolicMemoryUpdate -> copy(memory = memory.update(update.memoryUpdate))
        }

    fun multiUpdate(updates: Collection<SymbolicStateUpdate>): SymbolicState =
        updates.fold(this, SymbolicState::update)
}
sealed interface SymbolicStateUpdate : StateUpdate

data class SymbolicConstraintsUpdate(
    val constraints: Collection<KExpr<KBoolSort>> = emptyList()
) : SymbolicStateUpdate {
    constructor(constraint: KExpr<KBoolSort>) : this(listOf(constraint))
}

data class SymbolicMemoryUpdate(
    val memoryUpdate: MemoryUpdate
) : SymbolicStateUpdate