package org.utbot.cs.core.domain

import org.ksmt.expr.KExpr
import org.ksmt.sort.KBoolSort
import org.ksmt.sort.KBv32Sort
import soot.BooleanType
import soot.RefLikeType
import soot.RefType
import soot.Type

typealias AddrSort = KBv32Sort
typealias AddrExpr = KExpr<KBv32Sort>
typealias BoolExpr = KExpr<KBoolSort>


sealed interface SymbolicValue {
    val type: Type
}

data class PrimitiveValue(
    override val type: Type,
    val expr: KExpr<*>,
) : SymbolicValue

sealed interface ReferenceValue : SymbolicValue {
    val addr: AddrExpr
}

data class ArrayValue(
    override val type: Type,
    override val addr: AddrExpr,
) : ReferenceValue

data class ObjectValue(
    override val type: RefLikeType,
    override val addr: AddrExpr,
) : ReferenceValue

fun BoolExpr.toValue() = PrimitiveValue(BooleanType.v(), this)
