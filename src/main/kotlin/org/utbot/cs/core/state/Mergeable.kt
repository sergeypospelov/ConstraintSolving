package org.utbot.cs.core.state

import org.utbot.cs.core.svm.SymbolicVirtualMachine
import org.utbot.cs.core.symbolic.Memory
import org.utbot.cs.core.symbolic.SymbolicState
import org.utbot.cs.graph.Path
import kotlin.math.max
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap

interface Merger<T> {
    fun merge(lhs: T, rhs: T): T
}


class ExecutionStateMerger(
    val svm: SymbolicVirtualMachine,
) : Merger<ExecutionState> {

    override fun merge(lhs: ExecutionState, rhs: ExecutionState): ExecutionState {

        val pcLhs = svm.kContext.mkAnd(lhs.symbolicState.constraints)
        val pcRhs = svm.kContext.mkAnd(rhs.symbolicState.constraints)

        val localsLhs = lhs.symbolicState.memory.locals
        val localsRhs = rhs.symbolicState.memory.locals
        val localKeys = localsLhs.locals.keys + localsRhs.locals.keys

        val localsState = localKeys.associateWith { localId ->
            val localLhs = localsLhs.locals[localId] ?: svm.createConst(localId.type)
            val localRhs = localsRhs.locals[localId] ?: svm.createConst(localId.type)

            val local = svm.ite(pcLhs, localLhs, localRhs)
            local
        }
        val allDescriptors = lhs.symbolicState.memory.fieldsState.keys + rhs.symbolicState.memory.fieldsState.keys

        val fieldsState = allDescriptors.associateWith { descriptor ->
            val fieldArrayLhs = lhs.symbolicState.memory.findArray(svm.kContext, descriptor)
            val fieldArrayRhs = rhs.symbolicState.memory.findArray(svm.kContext, descriptor)

            val fieldArray = svm.ite(pcLhs, fieldArrayLhs, fieldArrayRhs)
            fieldArray
        }


        val memory = Memory(fieldsState.toPersistentMap(), LocalsStorage(localsState.toPersistentMap()))
        val pc = svm.kContext.mkOr(pcLhs, pcRhs)
        val symbolicState = SymbolicState(
            persistentListOf(pc),
            memory,
            max(lhs.symbolicState.findNewAddr(), rhs.symbolicState.findNewAddr()) - 1
        )


        val path = mergePaths(lhs.path, rhs.path)
        val symbolicStateWithSvmUpdates = svm.collectUpdates().fold(symbolicState, SymbolicState::update)

        return ExecutionState(
            path,
            lhs.executionStack,
            symbolicStateWithSvmUpdates,
            lhs.timesMerged + rhs.timesMerged + 1,
        )
    }

    private fun mergePaths(lhs: Path, rhs: Path): Path {
        require(lhs.last() == rhs.last()) {
            "The last node in the paths must be the same, but lhs.last() = ${lhs.last()} and rhs.last() = ${rhs.last()}"
        }

        val idx = computeLastCommonIndex(lhs, rhs)

        return Path(lhs.subList(0, idx + 1) + lhs.last())
    }
}

private fun computeLastCommonIndex(lhs: Path, rhs: Path): Int {
    val maxSize = Math.min(lhs.size, rhs.size)
    var power2 = Int.SIZE_BITS - maxSize.countLeadingZeroBits() - 1

    var idx = -1

    while (power2 >= 0) {
        val possibleNextIdx = idx + (1 shl power2)
        if (possibleNextIdx < maxSize && lhs[possibleNextIdx] == rhs[possibleNextIdx]) {
            idx = possibleNextIdx
        }
        power2--
    }

    return idx
}

interface ExecutionStateMerging<Slice, Node, MergedSlice, PrefixState> {
    fun computeLastCommonNode(lhs: ExecutionState, rhs: ExecutionState): Node

    fun extractSlice(executionState: ExecutionState, node: Node): Slice

    fun mergeSlices(lhs: Slice, rhs: Slice): MergedSlice

    fun extractPrefixState(lhs: ExecutionState, rhs: ExecutionState, node: Node): PrefixState

    fun applyMergedSlice(prefixState: PrefixState, mergedSlice: MergedSlice): ExecutionState
}

//class ExecutionStateMergingImpl(
//    val svm: SymbolicVirtualMachine
//) :
//    ExecutionStateMerging<
//        ExecutionStateMergingImpl.Slice,
//        ExecutionStateMergingImpl.Node,
//        ExecutionStateMergingImpl.FoldedSlices,
//        ExecutionStateMergingImpl.PrefixState
//        > {
//
//    class Slice(
//        val updates: Collection<ExecutionStateUpdate>,
//    )
//
//
//    class Node(
//        val idx: Int,
//    )
//
//    class FoldedSlices(
//        lhsData: Data,
//        rhsData: Data,
//    ) {
//        class Data(
//            val touchedLocals: Set<LocalId>,
//            val touchedChunkDescriptors: Set<ChunkDescriptor>,
//            val pc: Set<KExpr<KBoolSort>>,
//        )
//
//        val leftPc = lhsData.pc.toList()
//        val rightPc = rhsData.pc.toList()
//        val allTouchedLocals: Set<LocalId> = lhsData.touchedLocals + rhsData.touchedLocals
//        val allTouchedChunkDescriptors: Set<ChunkDescriptor>
//        = lhsData.touchedChunkDescriptors + rhsData.touchedChunkDescriptors
//    }
//
//    class PrefixState(
//        val lhs: ExecutionState,
//        val rhs: ExecutionState,
//    )
//
//    override fun computeLastCommonNode(lhs: ExecutionState, rhs: ExecutionState): Node {
//        val maxSize = Math.min(lhs.path.size, rhs.path.size)
//        var power2 = Int.SIZE_BITS - maxSize.countLeadingZeroBits() - 1
//
//        var idx = -1
//
//        while (power2 >= 0) {
//            val possibleNextIdx = idx + (1 shl power2)
//            if (possibleNextIdx < maxSize && lhs.path[possibleNextIdx] == rhs.path[possibleNextIdx]) {
//                idx = possibleNextIdx
//            }
//            power2--
//        }
//
//        return Node(idx)
//    }
//
//    override fun extractSlice(executionState: ExecutionState, node: Node): Slice {
//        val idx = node.idx
//        val size = executionState.history.size
//        val updates = executionState.history.slice(idx, size)
//        return Slice(updates)
//    }
//
//    override fun mergeSlices(lhs: Slice, rhs: Slice): FoldedSlices {
//        val lhsData = obtainData(lhs)
//        val rhsData = obtainData(rhs)
//        return FoldedSlices(lhsData, rhsData)
//    }
//
//    override fun extractPrefixState(lhs: ExecutionState, rhs: ExecutionState, node: Node): PrefixState {
//
//    }
//
//    override fun applyMergedSlice(prefixState: PrefixState, mergedSlice: FoldedSlices): ExecutionState {
//        val allTouchedLocals = mergedSlice.allTouchedLocals
//        val allTouchedChunkDescriptors = mergedSlice.allTouchedChunkDescriptors
//
//        val leftPc = svm.kContext.mkAnd(mergedSlice.leftPc)
//        val rightPc = svm.kContext.mkAnd(mergedSlice.rightPc)
//
//        val mergedLocals = allTouchedLocals.associateWith { localId ->
//            val lhsValue = prefixState.lhs.locals[localId]!! // TODO: smells
//            val rhsValue = prefixState.rhs.locals[localId]!! // TODO: smells
//            val newLocal = svm.ite(leftPc, lhsValue, rhsValue)
//            newLocal
//        }
//
//        val mergedMemory = allTouchedChunkDescriptors.associateWith { chunkDescriptor ->
//            val lhsValue = prefixState.lhs.symbolicState.memory.findArray(svm.kContext, chunkDescriptor)
//            val rhsValue = prefixState.rhs.symbolicState.memory.findArray(svm.kContext, chunkDescriptor)
//            val newArray = svm.ite(leftPc, lhsValue, rhsValue)
//            newArray
//        }
//
//        val leftAllPc = svm.kContext.mkAnd(prefixState.lhs.symbolicState.constraints)
//        val rightAllPc = svm.kContext.mkAnd(prefixState.rhs.symbolicState.constraints)
//        val newConstraints = svm.kContext.mkOr(leftAllPc, rightAllPc) // TODO: NOT OPTIMAL!!!
//
//        val newMemory = prefixState.rhs.symbolicState.memory.fieldsState.mutate { it.putAll(mergedMemory) }
//        val newLocals = LocalsStorage(mergedLocals.toPersistentMap())
//
//        val newExecutionState = ExecutionState(
//            prefixState.rhs.executionStack,
//            SymbolicState(persistentListOf(newConstraints), Memory(newMemory), )
//        )
//
//        return newExecutionState
//    }
//
//    private fun obtainData(slice: Slice): FoldedSlices.Data {
//        val localsState = mutableSetOf<LocalId>()
//        val pcState = mutableSetOf<KExpr<KBoolSort>>()
//        val memoryState = mutableSetOf<ChunkDescriptor>()
//
//        for (update in slice.updates) {
//            when (update) { // TODO: smells a lot
//                is ExecutionStateSymbolicUpdate -> when (val innerUpdate = update.symbolicStateUpdate) {
//                    is SymbolicConstraintsUpdate -> pcState.addAll(innerUpdate.constraints)
//                    is SymbolicMemoryUpdate -> when (val memoryUpdate = innerUpdate.memoryUpdate) {
//                        is ArrayStore -> memoryState += memoryUpdate.chunkDescriptor
//                    }
//                }
//
//                is LocalUpdate -> localsState += update.id
//                is MethodCall -> {}
//                NopUpdate -> {}
//                is ParameterDeclaration -> {}
//                is RetUpdate -> {}
//            }
//
//        }
//
//        return FoldedSlices.Data(localsState, memoryState, pcState)
//    }
//}