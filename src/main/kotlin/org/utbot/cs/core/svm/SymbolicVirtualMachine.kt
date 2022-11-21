package org.utbot.cs.core.svm

import org.utbot.cs.core.domain.AddrExpr
import org.utbot.cs.core.domain.ArrayValue
import org.utbot.cs.core.domain.BoolExpr
import org.utbot.cs.core.domain.ObjectValue
import org.utbot.cs.core.domain.PrimitiveValue
import org.utbot.cs.core.domain.ReferenceValue
import org.utbot.cs.core.domain.SymbolicValue
import org.utbot.cs.core.state.LocalId
import org.utbot.cs.core.symbolic.ArrayStore
import org.utbot.cs.core.symbolic.ChunkDescriptor
import org.utbot.cs.core.symbolic.LocalUpdate
import org.utbot.cs.core.symbolic.SymbolicConstraintsUpdate
import org.utbot.cs.core.symbolic.SymbolicMemoryUpdate
import org.utbot.cs.core.symbolic.SymbolicState
import org.utbot.cs.core.symbolic.SymbolicStateUpdate
import org.utbot.cs.core.util.Eq
import org.utbot.cs.core.util.Gt
import org.utbot.cs.core.util.KVariable
import org.utbot.cs.core.util.Lt
import org.utbot.cs.core.util.Operator
import org.utbot.cs.core.util.Or
import org.utbot.cs.core.util.convertVar
import org.utbot.cs.core.util.createConst
import org.utbot.cs.core.util.doOperation
import org.utbot.cs.core.util.negate
import org.ksmt.KContext
import org.ksmt.expr.KExpr
import org.ksmt.sort.KBoolSort
import org.ksmt.sort.KSort
import org.ksmt.utils.asExpr
import org.ksmt.utils.mkConst
import soot.ArrayType
import soot.BooleanType
import soot.ByteType
import soot.CharType
import soot.DoubleType
import soot.FloatType
import soot.IntType
import soot.LongType
import soot.NullType
import soot.RefLikeType
import soot.RefType
import soot.ShortType
import soot.SootField
import soot.Type
import soot.VoidType
import soot.jimple.BinopExpr
import soot.jimple.ClassConstant
import soot.jimple.Constant
import soot.jimple.DoubleConstant
import soot.jimple.FloatConstant
import soot.jimple.IntConstant
import soot.jimple.LongConstant
import soot.jimple.NullConstant
import soot.jimple.StringConstant

private var localCounter = 0
private var unboundedCounter = 0

class SymbolicVirtualMachine(
    val kContext: KContext,
    val symbolicState: SymbolicState,
    val typeRegistry: TypeRegistry,
) {
    private val updates = mutableListOf<SymbolicStateUpdate>()

    fun voidValue(): SymbolicValue = PrimitiveValue(VoidType.v(), kContext.mkBv(0))
    fun nullValue(): SymbolicValue = ObjectValue(NullType.v(), kContext.mkBv(0))

    fun collectUpdates(): List<SymbolicStateUpdate> = updates.toList().also { updates.clear() } // copy then clear

    fun createConst(type: Type): SymbolicValue {
        val expr = kContext.createConst(type, "local_${localCounter++}")

        if (type is RefType) {
            val typeConstraint = typeRegistry.mkTypeConstraint(expr as AddrExpr, type)
            val isNullConstraint = typeRegistry.mkIsNullConstraint(expr)

            updates += SymbolicConstraintsUpdate(kContext.mkOr(typeConstraint, isNullConstraint))
        }

        return wrapKExpr(type, expr)
    }

    fun createLocal(localId: LocalId): SymbolicValue {
        val value = createConst(localId.type)

        val memoryUpdate = SymbolicMemoryUpdate(LocalUpdate(localId, value))

        updates += memoryUpdate

        return value
    }

    fun updateLocal(localId: LocalId, value: SymbolicValue) {
        val memoryUpdate = SymbolicMemoryUpdate(LocalUpdate(localId, value))
        updates += memoryUpdate
    }

    fun getLocal(localId: LocalId): SymbolicValue {
        return symbolicState.memory.findLocal(localId) ?: error("Can't find a local with such name")
    }

    fun createNewObject(type: RefType): ObjectValue {
        val addr = symbolicState.findNewAddr()
        val addrExpr = kContext.mkBv(addr)
        val objectValue = wrapKExpr(type, addrExpr)
        val typeConstraint = typeRegistry.mkTypeConstraint(addrExpr, type)

        updates += SymbolicConstraintsUpdate(typeConstraint)

        return objectValue as ObjectValue
    }

    fun createNewArray(type: RefType): ArrayValue {
        TODO()
    }

    fun createPrimitive(type: Type): PrimitiveValue {
        TODO()
    }

    fun selectFromField(symbolicValue: ObjectValue, field: SootField): SymbolicValue {
        val array = symbolicState.memory.findArray(kContext, ChunkDescriptor(symbolicValue.type as RefType, field))
        return wrapKExpr(field.type, kContext.mkArraySelect(array, symbolicValue.addr))
    }

    fun putIntoField(thisObject: ObjectValue, field: SootField, fieldObject: SymbolicValue) {
        updates += SymbolicMemoryUpdate(
            memoryUpdate = ArrayStore(
                ChunkDescriptor(thisObject.type as RefType, field),
                thisObject.addr,
                unwrap(fieldObject) as KExpr<KSort>
            )
        )
    }

    fun selectFromArray(arrayValue: ArrayValue, index: PrimitiveValue): SymbolicValue {
        TODO()
    }

    fun storeInArray(arrayValue: ArrayValue, index: PrimitiveValue): SymbolicValue {
        TODO()
    }

    fun createConstant(constant: Constant): SymbolicValue {
        if (constant is NullConstant) {
            return nullValue()
        }
        val expr = when (constant) {
            is IntConstant -> kContext.mkBv(constant.value)
            is LongConstant -> kContext.mkBv(constant.value)
            is FloatConstant -> kContext.mkFp32(constant.value)
            is DoubleConstant -> kContext.mkFp64(constant.value)
            is StringConstant ->  {
                kContext.mkBoolSort().mkConst("unbounded_${unboundedCounter++}")
//                TODO()
//                val addr = findNewAddr()
//                val refType = constant.type as RefType
//
//                // We disable creation of string literals to avoid unsats because of too long lines
//                if (UtSettings.ignoreStringLiterals && constant.value.length > MAX_STRING_SIZE) {
//                    // instead of it we create an unbounded symbolic variable
//                    workaround(HACK) {
//                        offerState(environment.state.withLabel(StateLabel.CONCRETE))
//                        createObject(addr, refType, useConcreteType = true)
//                    }
//                } else {
//                    val typeStorage = TypeStorage.constructTypeStorageWithSingleType(refType)
//                    val typeConstraint = typeRegistry.typeConstraint(addr, typeStorage).all().asHardConstraint()
//
//                    queuedSymbolicStateUpdates += typeConstraint
//
//                    objectValue(refType, addr, StringWrapper()).also {
//                        initStringLiteral(it, constant.value)
//                    }
//                }
            }

            is ClassConstant -> {
                kContext.mkBoolSort().mkConst("unbounded_${unboundedCounter++}")
//                TODO()
//                val sootType = constant.toSootType()
//                val result = if (sootType is RefLikeType) {
//                    typeRegistry.createClassRef(sootType.baseType, sootType.numDimensions)
//                } else {
//                    error("Can't get class constant for ${constant.value}")
//                }
//                queuedSymbolicStateUpdates += result.symbolicStateUpdate
//                (result.symbolicResult as SymbolicSuccess).value
            }

            else -> error("Unsupported type: $constant")
        }
        return wrapKExpr(constant.type, expr)
    }

    fun doReferenceBinaryOperation(binopExpr: BinopExpr, left: ReferenceValue, right: ReferenceValue): PrimitiveValue {
        val expr = doOperation(binopExpr, left.addr, right.addr)
        return PrimitiveValue(binopExpr.type, expr)
    }

    fun assert(primitive: PrimitiveValue) {
        val constraint = primitive.expr.asExpr(kContext.boolSort)
        updates += SymbolicConstraintsUpdate(constraint)
    }

    fun doPrimitiveBinaryOperation(binopExpr: BinopExpr, left: PrimitiveValue, right: PrimitiveValue): PrimitiveValue {
        val expr = doOperation(binopExpr, left.expr, right.expr)
        return PrimitiveValue(binopExpr.type, expr)
    }

    fun doNegate(arg: PrimitiveValue): PrimitiveValue {
        return PrimitiveValue(arg.type, kContext.negate(arg.expr))
    }

    fun doOr(left: PrimitiveValue, right: PrimitiveValue): BoolExpr =
        doOperation(Or, left, right).asExpr(kContext.boolSort)

    fun doEq(left: PrimitiveValue, right: PrimitiveValue): BoolExpr =
        doOperation(Eq, left, right).asExpr(kContext.boolSort)

    fun doLt(left: PrimitiveValue, right: PrimitiveValue): BoolExpr =
        doOperation(Lt, left, right).asExpr(kContext.boolSort)


    fun doGt(left: PrimitiveValue, right: PrimitiveValue): BoolExpr =
        doOperation(Gt, left, right).asExpr(kContext.boolSort)

    fun doOperation(operator: Operator, arg1: PrimitiveValue, arg2: PrimitiveValue): KExpr<*> =
        operator.invoke(arg1.expr, arg2.expr)

    fun intValue(int: Int): PrimitiveValue = PrimitiveValue(IntType.v(), kContext.mkBv(int))

    fun boolValue(bool: Boolean): PrimitiveValue =
        PrimitiveValue(BooleanType.v(), if (bool) kContext.mkTrue() else kContext.mkFalse())

    private fun wrapKExpr(type: Type, expr: KExpr<*>): SymbolicValue =
        when (type) {
            is ByteType,
            is ShortType,
            is IntType,
            is LongType,
            is FloatType,
            is DoubleType,
            is BooleanType,
            is CharType,
            -> PrimitiveValue(type, expr)

            is RefType -> ObjectValue(type, expr as AddrExpr)
            is ArrayType -> ArrayValue(type, expr as AddrExpr)
            is NullType -> nullValue()
            else -> error("Can't create const for ${type::class.java}")
        }

    private fun unwrap(value: SymbolicValue): KExpr<*> =
        when (value) {
            is PrimitiveValue -> value.expr
            is ReferenceValue -> value.addr
        }

    fun ite(condition: KExpr<KBoolSort>, lhs: SymbolicValue, rhs: SymbolicValue): SymbolicValue {
        val constraint = condition
        val value = when {
            lhs is PrimitiveValue && rhs is PrimitiveValue && lhs.type == rhs.type ->
                wrapKExpr(lhs.type, kContext.mkIte(constraint, lhs.expr as KExpr<KSort>, rhs.expr as KExpr<KSort>))

            lhs is ReferenceValue && rhs is ReferenceValue -> {
                val type = when {
                    lhs.type == rhs.type -> lhs.type
                    lhs.type == NullType.v() -> rhs.type
                    rhs.type == NullType.v() -> lhs.type
                    else -> error("Can't match types: ${lhs.type} and ${rhs.type}")
                }
                wrapKExpr(type, kContext.mkIte(constraint, lhs.addr, rhs.addr))
            }

            else -> error("Can't match types: ${lhs.type} and ${rhs.type}")
        }
        return value
    }


    fun <T : KSort> ite(condition: KExpr<KBoolSort>, lhs: KExpr<T>, rhs: KExpr<T>): KExpr<T> {
        val value = kContext.mkIte(condition, lhs, rhs)
        return value
    }

    fun cast(value: SymbolicValue, toType: Type): SymbolicValue  {
        if (value.type == toType) {
            return value
        }

        if (value is PrimitiveValue) {
            val kVariable = KVariable(value.expr, value.type)
            val (newExpr, _) = kContext.convertVar(kVariable, toType)
            return PrimitiveValue(toType, newExpr)

        } else {
            return createConst(toType)
        }
    }
}

