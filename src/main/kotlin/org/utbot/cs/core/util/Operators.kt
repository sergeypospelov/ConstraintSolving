package org.utbot.cs.core.util

import com.microsoft.z3.Context
import org.ksmt.expr.KFpRoundingMode
import org.ksmt.KContext
import org.ksmt.expr.KExpr
import org.ksmt.sort.KBoolSort
import org.ksmt.sort.KBvSort
import org.ksmt.sort.KFpSort
import org.ksmt.sort.KSort
import soot.ByteType
import soot.CharType
import soot.IntType
import soot.Type
import soot.jimple.BinopExpr
import soot.jimple.internal.*
import kotlin.reflect.KClass

fun doOperation(
    sootExpression: BinopExpr,
    left: KExpr<*>,
    right: KExpr<*>
): KExpr<*> = ops.get(sootExpression::class)?.invoke(left, right)
    ?: error("Can't find operator for ${sootExpression::class}")

private val ops: Map<KClass<*>, Operator> = mapOf(
    JLeExpr::class to Le,
    JLtExpr::class to Lt,
    JGeExpr::class to Ge,
    JGtExpr::class to Gt,
    JEqExpr::class to Eq,
    JNeExpr::class to Ne,
    JDivExpr::class to Div,
    JRemExpr::class to Rem,
    JMulExpr::class to Mul,
    JAddExpr::class to Add,
    JSubExpr::class to Sub,
//    JShlExpr::class to Shl, TODO
//    JShrExpr::class to Shr, TODO
//    JUshrExpr::class to Ushr, TODO
    JXorExpr::class to Xor,
    JOrExpr::class to Or,
    JAndExpr::class to And,
    JCmpExpr::class to Cmp,
    JCmplExpr::class to Cmpl,
    JCmpgExpr::class to Cmpg
)

// internal

sealed class Operator(
    private val onBv: KContext.(KExpr<KBvSort>, KExpr<KBvSort>) -> KExpr<*> = { _, _ -> error("Unimplemented for bvs") },
    private val onFp: KContext.(KExpr<KFpSort>, KExpr<KFpSort>) -> KExpr<*> = { _, _ -> error("Unimplemented for fps") },
    private val onBool: KContext.(KExpr<KBoolSort>, KExpr<KBoolSort>) -> KExpr<*> = { _, _ -> error("Unimplemented for bools") }
) {
    open operator fun invoke(left: KExpr<*>, right: KExpr<*>): KExpr<*> =
        left.ctx.alignVars(left, right).let { (aleft, aright) ->
            doAction(aleft, aright)
        }

    protected fun doAction(left: KExpr<*>, right: KExpr<*>): KExpr<*> =
        when {
            left.sort() is KBvSort && right.sort() is KBvSort -> left.ctx.onBv(
                left as KExpr<KBvSort>,
                right as KExpr<KBvSort>
            )

            left.sort() is KFpSort && right.sort() is KFpSort -> left.ctx.onFp(
                left as KExpr<KFpSort>,
                right as KExpr<KFpSort>
            )

            left.sort() is KBoolSort && right.sort() is KBoolSort -> left.ctx.onBool(
                left as KExpr<KBoolSort>,
                right as KExpr<KBoolSort>
            )

            else -> error("Can't match sorts: ${left.sort()} and ${right.sort( )}")
        }

}

// predicates
object Le : Operator(KContext::mkBvSignedLessExpr, KContext::mkFpLessOrEqualExpr)
object Lt : Operator(KContext::mkBvSignedLessExpr, KContext::mkFpLessExpr)
object Ge : Operator(KContext::mkBvSignedGreaterOrEqualExpr, KContext::mkFpGreaterOrEqualExpr)
object Gt : Operator(KContext::mkBvSignedGreaterExpr, KContext::mkFpGreaterExpr)
object Eq : Operator(KContext::mkEq, KContext::mkFpEqualExpr, KContext::mkEq)
object Ne : Operator(KContext::ne, KContext::fpNe, KContext::ne)

// math
object Rem : Operator(KContext::mkBvSignedRemExpr, KContext::mkFpRemExpr)
object Div : Operator(KContext::mkBvSignedDivExpr, KContext::fpDiv)
object Mul : Operator(KContext::mkBvMulExpr, KContext::fpMul)
object Add : Operator(KContext::mkBvAddExpr, KContext::fpAdd)
object Sub : Operator(KContext::mkBvSubExpr, KContext::fpSub)

// boolean
object Xor : Operator(KContext::mkBvXorExpr, onBool = KContext::mkXor)
object Or : Operator(KContext::mkBvOrExpr, onBool = KContext::boolOr)
object And : Operator(KContext::mkBvAndExpr, onBool = KContext::boolAnd)

// cmp
internal object Cmp : Operator(KContext::bvCmp, KContext::fpCmp)
internal object Cmpl : Operator(KContext::bvCmp, KContext::fpCmpl)
internal object Cmpg : Operator(KContext::bvCmp, KContext::fpCmpg)

// internal helpful functions

private fun <T : KSort> KContext.ne(left: KExpr<T>, right: KExpr<T>): KExpr<KBoolSort> = mkNot(mkEq(left, right))
private fun KContext.fpNe(left: KExpr<KFpSort>, right: KExpr<KFpSort>): KExpr<KBoolSort> =
    mkNot(mkFpEqualExpr(left, right))

private fun KContext.boolOr(left: KExpr<KBoolSort>, right: KExpr<KBoolSort>): KExpr<KBoolSort> = mkOr(left, right)
private fun KContext.boolAnd(left: KExpr<KBoolSort>, right: KExpr<KBoolSort>): KExpr<KBoolSort> = mkAnd(left, right)

private fun KContext.fpDiv(left: KExpr<KFpSort>, right: KExpr<KFpSort>): KExpr<KFpSort> =
    mkFpDivExpr(mkFpRoundingModeExpr(KFpRoundingMode.RoundNearestTiesToEven), left, right)

private fun KContext.fpMul(left: KExpr<KFpSort>, right: KExpr<KFpSort>): KExpr<KFpSort> =
    mkFpMulExpr(mkFpRoundingModeExpr(KFpRoundingMode.RoundNearestTiesToEven), left, right)

private fun KContext.fpAdd(left: KExpr<KFpSort>, right: KExpr<KFpSort>): KExpr<KFpSort> =
    mkFpAddExpr(mkFpRoundingModeExpr(KFpRoundingMode.RoundNearestTiesToEven), left, right)

private fun KContext.fpSub(left: KExpr<KFpSort>, right: KExpr<KFpSort>): KExpr<KFpSort> =
    mkFpSubExpr(mkFpRoundingModeExpr(KFpRoundingMode.RoundNearestTiesToEven), left, right)


private fun KContext.bvCmp(left: KExpr<KBvSort>, right: KExpr<KBvSort>): KExpr<KBvSort> =
    mkIte(
        mkBvSignedLessOrEqualExpr(left, right),
        mkIte(mkEq(left, right), mkBv(0, 32u), mkBv(-1, 32u)),
        mkBv(1, 32u)
    )

private fun KContext.fpCmp(left: KExpr<KFpSort>, right: KExpr<KFpSort>): KExpr<KBvSort> =
    mkIte(
        mkFpLessOrEqualExpr(left, right),
        mkIte(mkFpEqualExpr(left, right), mkBv(0, 32u), mkBv(-1, 32u)),
        mkBv(1, 32u)
    )

private fun KContext.fpCmpl(left: KExpr<KFpSort>, right: KExpr<KFpSort>): KExpr<KBvSort> =
    mkIte(
        mkOr(mkFpIsNaNExpr(left), mkFpIsNaNExpr(right)), mkBv(-1, 32u),
        mkIte(
            mkFpLessOrEqualExpr(left, right),
            mkIte(mkFpEqualExpr(left, right), mkBv(0, 32u), mkBv(-1, 32u)),
            mkBv(1, 32u)
        )
    )

private fun KContext.fpCmpg(left: KExpr<KFpSort>, right: KExpr<KFpSort>): KExpr<KBvSort> =
    mkIte(
        mkOr(mkFpIsNaNExpr(left), mkFpIsNaNExpr(right)), mkBv(1, 32u),
        mkIte(
            mkFpLessOrEqualExpr(left, right),
            mkIte(mkFpEqualExpr(left, right), mkBv(0, 32u), mkBv(-1, 32u)),
            mkBv(1, 32u)
        )
    )

// aligning

private fun KContext.alignVars(left: KExpr<*>, right: KExpr<*>): Pair<KExpr<*>, KExpr<*>> {
    val maxSort = maxOf(left.sort(), right.sort(), mkBv32Sort(), compareBy { it.rank() })
    return convertVar(left, maxSort) to convertVar(right, maxSort)
}

private fun KSort.rank(): UInt = when (this) {
    is KFpSort -> 30000000u + this.exponentBits + this.significandBits
    is KBoolSort -> 20000000u
    is KBvSort -> 10000000u + this.sizeBits
    else -> error("Wrong sort $this")
}

data class KVariable(
    val expr: KExpr<*>,
    val type: Type
)

fun KContext.convertVar(variable: KVariable, toType: Type): KVariable {
    if (toType == variable.type) return variable
    if (variable.type is ByteType && toType is CharType) {
        return convertVar(convertVar(variable, IntType.v()), toType)
    }
    return KVariable(convertVar(variable.expr, toType.toKSort(this)), toType)
}

private fun KContext.convertVar(expr: KExpr<*>, sort: KSort): KExpr<*> {
    val exprSort = expr.sort()
    return when {
        sort == exprSort -> expr
        sort is KFpSort && exprSort is KFpSort -> mkFpToFpExpr(
            sort,
            mkFpRoundingModeExpr(KFpRoundingMode.RoundNearestTiesToEven),
            expr as KExpr<out KFpSort>,
        )
        sort is KFpSort && exprSort is KBvSort -> mkBvToFpExpr(sort, mkFpRoundingModeExpr(KFpRoundingMode.RoundNearestTiesToEven), expr as KExpr<KBvSort>, true) // TODO variable.unsigned
        sort is KBvSort && exprSort is KFpSort -> convertFPtoBV(expr as KExpr<KFpSort>, sort.sizeBits)
        sort is KBoolSort && exprSort is KBvSort -> Ne(expr, mkBv(0))
        sort is KBvSort && exprSort is KBvSort -> {
            val diff = sort.sizeBits.toInt() - exprSort.sizeBits.toInt()
            if (diff > 0) {
                if (false) { // TODO variable.unsigned
                    mkBvZeroExtensionExpr(diff, expr as KExpr<KBvSort>)
                } else {
                    mkBvSignExtensionExpr(diff, expr as KExpr<KBvSort>)
                }
            } else {
                mkBvExtractExpr(sort.sizeBits.toInt() - 1, 0, expr as KExpr<KBvSort>)
            }
        }

        else -> error("Wrong expr $expr and sort $sort")
    }
}

/**
 * Converts FP to BV covering Java logic:
 * - convert to long for long
 * - convert to int for other types, with second step conversion by taking lowest bits
 * (can lead to significant changes if value outside of target type range).
 */
private fun KContext.convertFPtoBV(expr: KExpr<KFpSort>, sortSize: UInt): KExpr<KBvSort> = when (sortSize) {
    Long.SIZE_BITS.toUInt() -> convertFPtoBV(expr, sortSize, Long.MIN_VALUE, Long.MAX_VALUE)
    else -> doNarrowConversion(
        convertFPtoBV(expr, Int.SIZE_BITS.toUInt(), Int.MIN_VALUE, Int.MAX_VALUE),
        sortSize
    )
}

/**
 * Converts FP to BV covering special cases for NaN, Infinity and values outside of [minValue, maxValue] range.
 */
private fun KContext.convertFPtoBV(expr: KExpr<KFpSort>, sortSize: UInt, minValue: Number, maxValue: Number): KExpr<KBvSort> =
    mkIte(
        mkFpIsNaNExpr(expr),
        makeBV(0, sortSize),
        mkIte(
            Lt(expr, makeFP(minValue, expr.sort())) as KExpr<KBoolSort>,
            makeBV(minValue, sortSize),
            mkIte(
                Gt(expr, makeFP(maxValue, expr.sort())) as KExpr<KBoolSort>,
                makeBV(maxValue, sortSize),
                mkFpToBvExpr(mkFpRoundingModeExpr(KFpRoundingMode.RoundNearestTiesToEven), expr, sortSize.toInt(), true)
            )
        )
    )

/**
 * Converts int to byte, short or char.
 */
internal fun KContext.doNarrowConversion(expr: KExpr<KBvSort>, sortSize: UInt): KExpr<KBvSort> = when (sortSize) {
    expr.sort.sizeBits -> expr
    else -> mkBvExtractExpr(sortSize.toInt() - 1, 0, expr)
}

fun KContext.makeBV(const: Number, size: UInt): KExpr<KBvSort> = when (const) {
    is Byte -> mkBv(const.toInt(), size)
    is Short -> mkBv(const.toInt(), size)
    is Int -> mkBv(const, size)
    is Long -> mkBv(const, size)
    else -> error("Wrong type ${const::class}")
}

fun KContext.makeFP(const: Number, sort: KFpSort): KExpr<KFpSort> = when (const) {
    is Float -> mkFp(const, sort)
    is Double -> mkFp(const, sort)
    is Int -> mkBvToFpExpr(sort, mkFpRoundingModeExpr(KFpRoundingMode.RoundNearestTiesToEven), mkBv(const, Int.SIZE_BITS.toUInt()), true)
    is Long -> mkBvToFpExpr(sort, mkFpRoundingModeExpr(KFpRoundingMode.RoundNearestTiesToEven), mkBv(const, Long.SIZE_BITS.toUInt()), true)
    else -> error("Wrong type ${const::class}")
}

fun KContext.negate(expr: KExpr<*>): KExpr<*> = when (expr.sort()) {
    is KBvSort -> mkBvNegationExpr(expr as KExpr<KBvSort>)
    is KFpSort -> mkFpNegationExpr(expr as KExpr<KFpSort>)
    is KBoolSort -> mkNot(expr as KExpr<KBoolSort>)
    else -> error("Unsupported expr: ${expr}")
}