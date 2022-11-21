package org.utbot.cs.core.symbolic

import org.utbot.cs.core.domain.AddrSort
import org.utbot.cs.core.domain.SymbolicValue
import org.utbot.cs.core.state.LocalId
import org.utbot.cs.core.state.LocalsStorage
import org.utbot.cs.core.state.State
import org.utbot.cs.core.state.StateUpdate
import org.utbot.cs.core.util.mkAddrSort
import org.utbot.cs.core.util.toKSort
import org.ksmt.KContext
import org.ksmt.expr.KExpr
import org.ksmt.sort.KArraySort
import org.ksmt.sort.KSort
import org.ksmt.utils.mkConst
import soot.ArrayType
import soot.RefLikeType
import soot.RefType
import soot.SootField
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

data class Memory(
    val fieldsState: PersistentMap<ChunkDescriptor, KExpr<KArraySort<AddrSort, KSort>>> = persistentMapOf(),
    val locals: LocalsStorage = LocalsStorage()
) : State<Memory, MemoryUpdate> {

//    override fun merge(other: Memory): Memory {
//        TODO("Not yet implemented")
//    }

    fun findArray(ctx: KContext, chunkDescriptor: ChunkDescriptor) =
        fieldsState.getOrElse(chunkDescriptor) { ctx.defaultMakeArray(chunkDescriptor) }

    fun findLocal(localId: LocalId): SymbolicValue? {
        return locals[localId]
    }

    override fun update(update: MemoryUpdate): Memory {
        when (update) {
            is ArrayStore -> {
                val ctx = update.index.ctx
                val arr = findArray(ctx, update.chunkDescriptor)
                val newArray = ctx.mkArrayStore(arr, update.index, update.value)
                return copy(
                    fieldsState = fieldsState.put(update.chunkDescriptor, newArray)
                )
            }

            is ArraySet -> {
                return copy(
                    fieldsState = fieldsState.put(update.chunkDescriptor, update.newArray)
                )
            }

            is LocalUpdate -> {
                return copy(
                    locals = LocalsStorage(locals.locals.put(update.id, update.symbolicValue))
                )
            }
        }
    }
}

sealed interface MemoryUpdate : StateUpdate

data class ChunkDescriptor(
    val type: RefType,
    val field: SootField,
) {
    val id get() = type.className + "_" + field.name

    private val hashCode = id.hashCode()

    override fun hashCode(): Int = hashCode
}

fun KContext.defaultMakeArray(chunkDescriptor: ChunkDescriptor): KExpr<KArraySort<AddrSort, KSort>> =
    when (chunkDescriptor.field.type) {
        !is RefLikeType -> mkArraySort(mkAddrSort(), chunkDescriptor.field.type.toKSort(this)).mkConst(chunkDescriptor.id)
        is RefType -> mkArraySort(mkAddrSort(), mkAddrSort()).mkConst(chunkDescriptor.id)
        is ArrayType -> mkArraySort(mkAddrSort(), mkAddrSort()).mkConst(chunkDescriptor.id)
        else -> error("Unsupported type:  ${chunkDescriptor.field.type}")
    }

data class ArrayStore(
    val chunkDescriptor: ChunkDescriptor,
    val index: KExpr<AddrSort>,
    val value: KExpr<KSort>
) : MemoryUpdate

data class ArraySet(
    val chunkDescriptor: ChunkDescriptor,
    val newArray: KExpr<KArraySort<AddrSort, KSort>>
) : MemoryUpdate

data class LocalUpdate(
    val id: LocalId,
    val symbolicValue: SymbolicValue
) : MemoryUpdate
