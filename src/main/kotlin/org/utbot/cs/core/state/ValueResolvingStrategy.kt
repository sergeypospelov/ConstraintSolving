package org.utbot.cs.core.state

import org.utbot.cs.core.domain.ObjectValue
import org.utbot.cs.core.domain.SymbolicValue
import org.utbot.cs.core.svm.SymbolicVirtualMachine
import soot.SootField
import soot.Value

interface ValueResolvingStrategy {
    fun resolveLValue(value: Value): LValue
    fun resolveValue(value: Value): SymbolicValue
}

sealed interface LValue {
    fun update(svm: SymbolicVirtualMachine, symbolicValue: SymbolicValue)
}

class LocalRefLValue(
    val localId: LocalId,
) : LValue {
    override fun update(svm: SymbolicVirtualMachine, symbolicValue: SymbolicValue) {
        val value = svm.cast(symbolicValue, localId.type)
        svm.updateLocal(localId, value)
    }
}

class ArrayRefLValue(
) : LValue {
    override fun update(svm: SymbolicVirtualMachine, symbolicValue: SymbolicValue) {
    }
}

class InstanceFieldRefLValue(
    private val instance: ObjectValue,
    private val field: SootField,
) : LValue {
    override fun update(svm: SymbolicVirtualMachine, symbolicValue: SymbolicValue) {
        svm.putIntoField(instance, field, symbolicValue)
    }

}

class StaticInstanceFieldRefLValue : LValue {
    override fun update(svm: SymbolicVirtualMachine, symbolicValue: SymbolicValue) {
        TODO("Not yet implemented")
    }

}

