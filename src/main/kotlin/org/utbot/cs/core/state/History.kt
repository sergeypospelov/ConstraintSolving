package org.utbot.cs.core.state

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

interface History<TimeStamp : Comparable<TimeStamp>, Data, Slice> {
    fun record(timeStamp: TimeStamp, data: Data): History<TimeStamp, Data, Slice>
    fun reset(timeStamp: TimeStamp): History<TimeStamp, Data, Slice>
    fun slice(l: TimeStamp, r: TimeStamp): Slice
}

class UpdatesHistory(
    private val updates: PersistentList<Item> = persistentListOf()
) : History<Int, InternalExecutionStateUpdate, Collection<InternalExecutionStateUpdate>> {
    class Item(
        val timeStamp: Int,
        val update: InternalExecutionStateUpdate
    )

    override fun record(timeStamp: Int, data: InternalExecutionStateUpdate): UpdatesHistory {
        val item = Item(timeStamp, data)
        val newHistory = updates.add(item)
        return UpdatesHistory(newHistory)
    }

    override fun reset(timeStamp: Int): UpdatesHistory {
        var idx = updates.binarySearch { it.timeStamp.compareTo(timeStamp) }
        if (idx < 0) {
            idx = -idx - 1
        }
        val updates = updates.subList(0, idx + 1).toPersistentList() // optimize
        return UpdatesHistory(updates)
    }

    override fun slice(l: Int, r: Int): Collection<InternalExecutionStateUpdate> {
        return updates.subList(l, r).map { it.update }
    }

    val size get() = updates.size
}