import org.ksmt.KContext
import org.ksmt.expr.KExpr
import org.ksmt.solver.KSolverStatus
import org.ksmt.solver.z3.KZ3Solver
import org.ksmt.sort.KBv32Sort
import org.ksmt.utils.mkConst
import org.ksmt.utils.mkFreshConst
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

interface Hello<T> {
    fun go(): T
}

class HelloInt : Hello<Int> {
    override fun go() = 42
}

class HelloString : Hello<String> {
    override fun go() = "42"
}


fun main(args: Array<String>) {
    with(KContext()) {
        use {
            var arr = mkArraySort(mkBv32Sort(), mkBv32Sort()).mkConst("arr")
            arr = arr.store(1.toBv(), 1.toBv())

            val elem = arr.select(1.toBv())


            with(KZ3Solver(this)) {
                use {
                    val status = check()

                    println(status)
                    val model = model()
                    val res = model.eval(elem, true)
                    println(res)
                }
            }
        }
    }
}