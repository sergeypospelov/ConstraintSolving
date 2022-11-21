package org.utbot.cs.core.util

import org.ksmt.KContext
import org.ksmt.expr.KBitVecNumberValue
import org.ksmt.expr.KExpr
import org.ksmt.expr.KFalse
import org.ksmt.expr.KFpValue
import org.ksmt.expr.KTrue
import org.ksmt.sort.KSort
import org.ksmt.utils.mkConst
import soot.ArrayType
import soot.BooleanType
import soot.ByteType
import soot.CharType
import soot.DoubleType
import soot.FloatType
import soot.IntType
import soot.LongType
import soot.RefType
import soot.ShortType
import soot.Type

fun KContext.mkAddrSort() = mkBv32Sort()

fun Type.toKSort(context: KContext): KSort = when (this) {
    is ByteType -> context.mkBv8Sort()
    is ShortType -> context.mkBv16Sort()
    is IntType -> context.mkBv32Sort()
    is LongType -> context.mkBv64Sort()
    is FloatType -> context.mkFp32Sort()
    is DoubleType -> context.mkFp64Sort()
    is BooleanType -> context.mkBoolSort()
    is CharType -> context.mkBv16Sort()
    is ArrayType -> if (numDimensions == 1) {
        context.mkArraySort(context.mkAddrSort(), elementType.toKSort(context))
    } else {
        context.mkArraySort(context.mkAddrSort(), context.mkAddrSort())
    }

    is RefType -> context.mkAddrSort()
    else -> error("Cant' find KSort for ${this::class.java}!")
}

fun KContext.createConst(type: Type, name: String): KExpr<out KSort> =
    type.toKSort(this).mkConst(name)

fun KExpr<*>.value(unsigned: Boolean = false): Any = when (this) {
    is KBitVecNumberValue<*, *> -> toIntNum(unsigned)
    is KFpValue<*> -> TODO() // toFloatingPointNum()
    is KTrue -> true
    is KFalse -> false
    else -> error("Can't value the ${this::class}")
}

private fun KBitVecNumberValue<*, *>.toIntNum(unsigned: Boolean = false): Any = when (sort().sizeBits) {
    Byte.SIZE_BITS.toUInt() -> java.lang.Long.parseUnsignedLong(stringValue, 2).toByte()
    Short.SIZE_BITS.toUInt() -> if (unsigned) {
        java.lang.Long.parseUnsignedLong(stringValue, 2).toChar()
    } else {
        java.lang.Long.parseUnsignedLong(stringValue, 2).toShort()
    }
    Int.SIZE_BITS.toUInt() -> java.lang.Long.parseUnsignedLong(stringValue, 2).toInt()
    Long.SIZE_BITS.toUInt() -> java.lang.Long.parseUnsignedLong(stringValue, 2)
    // fallback for others
    else -> java.lang.Long.parseUnsignedLong(stringValue, 2).toInt()
}

//private fun KFpValue<*>.toFloatingPointNum(): Number = when (sort()) {
//    ctx.mkFp64Sort() -> when {
//        this.isNaN -> Double.NaN
//        this.isInf && this.isPositive -> Double.POSITIVE_INFINITY
//        this.isInf && this.isNegative -> Double.NEGATIVE_INFINITY
//        else -> {
//            val bitVecNum = ctx.mkFpToIEEEBvExpr(this) as KBitVec64Value
//
//            Double.fromBits(java.lang.Long.parseUnsignedLong(bitVecNum.toBinaryString(), 2))
//        }
//    }
//    ctx.mkFp32Sort() -> when {
//        this.isNaN -> Float.NaN
//        this.isInf && this.isPositive -> Float.POSITIVE_INFINITY
//        this.isInf && this.isNegative -> Float.NEGATIVE_INFINITY
//        else -> {
//            val bitVecNum = context.mkFPToIEEEBV(this).simplify() as BitVecNum
//
//            Float.fromBits(Integer.parseUnsignedInt(bitVecNum.toBinaryString(), 2))
//        }
//    }
//    else -> error("Unknown sort: $sort")
//}