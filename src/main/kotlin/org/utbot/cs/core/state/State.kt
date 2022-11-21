package org.utbot.cs.core.state

interface State<T : State<T, K>, K : StateUpdate> {
//    fun merge(other: T): T
    fun update(update: K): T
}

interface StateUpdate
//interface StateUpdater<T : State, U : StateUpdate> {
//    fun update(state: T, update: U): T
//}
//
