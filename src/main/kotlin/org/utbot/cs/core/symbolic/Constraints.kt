package org.utbot.cs.core.symbolic

import org.utbot.cs.core.state.State
import org.utbot.cs.core.state.StateUpdate
import kotlinx.collections.immutable.toPersistentList
import org.ksmt.expr.KExpr
import org.ksmt.sort.KBoolSort

class Constraints(
    constraints: Collection<KExpr<KBoolSort>>
) : State<Constraints, ConstraintsUpdate> {
    private val _constraints = constraints.toPersistentList()
    val constraints: List<KExpr<KBoolSort>>
        get() = _constraints

    //    override fun merge(other: Constraint): Constraint {
//        TODO("Not yet implemented")
//    }
//
    override fun update(update: ConstraintsUpdate): Constraints =
        Constraints(
            constraints = _constraints.addAll(update.constraints)
        )
}


data class ConstraintsUpdate(
    val constraints: Collection<KExpr<KBoolSort>> = emptyList()
) : StateUpdate {
    constructor(constraint: KExpr<KBoolSort>) : this(listOf(constraint))
}
