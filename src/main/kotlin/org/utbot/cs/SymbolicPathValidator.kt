package org.utbot.cs

import soot.jimple.Stmt

class Properties {

}

class SymbolicPathValidator(
    properties: Properties
): org.utbot.cs.PathValidator {
    override fun validatePath(path: Collection<Stmt>): Boolean {
        val pathRepresentation = buildPathRepresentation(path)
        return true
    }
}

private fun buildPathRepresentation(path: Collection<Stmt>) {

}