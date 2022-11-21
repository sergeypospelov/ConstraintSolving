package org.utbot.cs.core.svm

import org.utbot.cs.core.domain.AddrExpr
import org.utbot.cs.core.util.mkAddrSort
import org.ksmt.KContext
import org.ksmt.expr.KExpr
import org.ksmt.sort.KBoolSort
import org.ksmt.utils.mkConst
import soot.RefType

const val NULL_ADDRESS = 0

class TypeRegistry(
    val kContext: KContext
) {
    private val typeToId = mutableMapOf<RefType, Int>()

    private var typeIdPtr = 1

    fun getTypeId(type: RefType): Int =
        typeToId.getOrPut(type) { typeIdPtr++ }

    fun mkTypeConstraint(addr: AddrExpr, type: RefType): KExpr<KBoolSort> {
        val symType = kContext.mkArraySelect(addrToIdArray, addr)
        val id = getTypeId(type)
        return kContext.mkAnd(kContext.mkEq(symType, kContext.mkBv(id)), kContext.mkNot(mkIsNullConstraint(addr)))
    }

    fun mkIsNullConstraint(addr: AddrExpr): KExpr<KBoolSort> {
        return kContext.mkEq(addr, kContext.mkBv(NULL_ADDRESS))
    }

    fun mkIsNotNullConstraint(addr: AddrExpr): KExpr<KBoolSort> {
        return kContext.mkNot(mkIsNullConstraint(addr))
    }

    private val addrToIdArray = kContext.
        mkArraySort(kContext.mkAddrSort(), kContext.mkBv32Sort()).mkConst("addrToIdArray")
}