package org.utbot.cs.core.state

import org.utbot.cs.core.domain.SymbolicValue
import soot.SootMethod
import soot.Type
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

data class LocalId(val sootMethod: SootMethod, val name: String, val type: Type)

interface Locals {
    operator fun get(id: LocalId): SymbolicValue?

    operator fun set(id: LocalId, symbolicValue: SymbolicValue): Locals
}

class LocalsStorage(
    val locals: PersistentMap<LocalId, SymbolicValue> = persistentMapOf(),
) : Locals {

    override fun get(id: LocalId): SymbolicValue? =
        locals[id]

    override fun set(id: LocalId, symbolicValue: SymbolicValue) =
        LocalsStorage(locals.put(id, symbolicValue))
}
