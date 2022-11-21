package org.utbot.cs

import soot.jimple.Stmt

interface PathValidator {
    fun validatePath(list: Collection<Stmt>): Boolean
}