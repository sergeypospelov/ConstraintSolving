package org.utbot.cs.core.state

import org.utbot.cs.core.domain.ObjectValue
import org.utbot.cs.core.domain.PrimitiveValue
import org.utbot.cs.core.domain.ReferenceValue
import org.utbot.cs.core.domain.SymbolicValue
import org.utbot.cs.core.svm.SymbolicVirtualMachine
import soot.Local
import soot.SootMethod
import soot.Value
import soot.jimple.ArrayRef
import soot.jimple.BinopExpr
import soot.jimple.Constant
import soot.jimple.Expr
import soot.jimple.FieldRef
import soot.jimple.StaticFieldRef
import soot.jimple.internal.JCastExpr
import soot.jimple.internal.JInstanceFieldRef
import soot.jimple.internal.JInstanceOfExpr
import soot.jimple.internal.JLengthExpr
import soot.jimple.internal.JNegExpr
import soot.jimple.internal.JNewArrayExpr
import soot.jimple.internal.JNewExpr
import soot.jimple.internal.JNewMultiArrayExpr

class BaseValueResolvingStrategy(
    val sootMethod: SootMethod,
    val svm: SymbolicVirtualMachine,
) : ValueResolvingStrategy {
    override fun resolveLValue(value: Value): LValue =
        when (value) {
            is Local -> LocalRefLValue(LocalId(sootMethod, value.name, value.type))
            is FieldRef -> {
                val base = resolveInstanceForFieldRef(value)
                InstanceFieldRefLValue(base, value.field)
            }

            is ArrayRef -> ArrayRefLValue() // TODO
            else -> error("Unknown LValue: ${value::class}")
        }

    override fun resolveValue(value: Value): SymbolicValue =
        when (value) {
            is Local -> resolveLocal(value)
                ?: error("${value.name} is not found in the locals")
            is Constant -> resolveConstant(value)
            is Expr -> resolveExpr(value)
            is FieldRef -> resolveFieldRef(value)
            is ArrayRef -> resolveArrayRef(value)
            else -> error("Unknown RValue: ${value::class}")
        }

    private fun resolveArrayRef(value: ArrayRef): SymbolicValue =
        svm.createConst(value.type)

    private fun resolveInstanceForFieldRef(value: FieldRef): ObjectValue =
        when (value) {
            is JInstanceFieldRef -> resolveValue(value.base) as ObjectValue
            is StaticFieldRef -> svm.createConst(value.type) as ObjectValue // TODO
            else -> error("Unknown field ref")
        }


    private fun resolveFieldRef(value: FieldRef): SymbolicValue =
        when (value) {
            is JInstanceFieldRef -> {
                val base = resolveValue(value.base) as ObjectValue
                svm.selectFromField(base, value.field)
            }

            is StaticFieldRef -> {
                svm.createConst(value.type)
            }
            else -> error("Unknown field ref: $value")
        }


    fun resolveLocal(value: Local) =
        svm.symbolicState.memory.findLocal(LocalId(sootMethod, value.name, value.type))

    private fun resolveExpr(expr: Expr): SymbolicValue =
        when (expr) {
            is BinopExpr -> {
                val left = resolveValue(expr.op1)
                val right = resolveValue(expr.op2)
                when {
                    left is ReferenceValue && right is ReferenceValue -> {
                        svm.doReferenceBinaryOperation(expr, left, right)
                    }

                    left is PrimitiveValue && right is PrimitiveValue -> {
                        // division by zero special case
//                        if ((expr is JDivExpr || expr is JRemExpr) && left.expr.isInteger() && right.expr.isInteger()) {
//                            divisionByZeroCheck(right)
//                        }
                        // TODO division by zero check

                        svm.doPrimitiveBinaryOperation(expr, left, right)
                    }

                    else -> error("Unknown op $expr for $left and $right")
                }
            }

            is JNegExpr -> {
                val arg = resolveValue(expr.op) as PrimitiveValue
                svm.doNegate(arg)
            }

            is JNewExpr -> svm.createConst(expr.type) // TODO
            is JNewArrayExpr -> svm.createConst(expr.type) // TODO
            is JNewMultiArrayExpr -> svm.createConst(expr.type) // TODO
            is JLengthExpr -> svm.createConst(expr.type) // TODO
            is JCastExpr -> {
                val value = resolveValue(expr.op)
                svm.cast(value, expr.castType)
            }
            is JInstanceOfExpr -> svm.createConst(expr.type) // TODO
            else -> error("Unknown expression: $expr")
        }

    private fun resolveConstant(constant: Constant): SymbolicValue =
        svm.createConstant(constant)
}