import org.utbot.cs.core.Engine
import org.utbot.cs.graph.SinkStmt
import org.utbot.cs.soot.util.initSoot
import soot.Scene
import soot.SootMethod
import soot.jimple.Stmt

fun main() {
    initSoot("./build/classes/java/main")
    val clss = Scene.v().getSootClassUnsafe("org.utbot.cs.examples.Example")
    val mergeMethod = clss.methods.first { it.name == "bytesMagic" }
    println(mergeMethod.activeBody)


    val path = listOf(mergeMethod.activeBody.units.toList().last() as Stmt).map { SinkStmt(it, mergeMethod) }

    val engine = Engine()
    val status = engine.analyze(mergeMethod, path)

    println(status)
}

fun buildPath(mapping: List<Pair<SootMethod, List<Int>>>): List<Stmt> =
    mapping.flatMap { (method, indices) -> indices.map { method.activeBody.units.toList()[it] as Stmt } }