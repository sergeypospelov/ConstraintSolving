package org.utbot.cs.soot.util

import soot.G
import soot.PackManager
import soot.Scene
import soot.options.Options

fun initSoot(classpath: String?) {
    G.reset()
    val options = Options.v()

    options.apply {
        set_prepend_classpath(true)
        // set true to debug. Disabled because of a bug when two different variables
        // from the source code have the same name in the jimple body.
        setPhaseOption("jb", "use-original-names:false")
        set_soot_classpath("")
        set_src_prec(Options.src_prec_only_class)
        set_process_dir(listOf(classpath))
        set_keep_line_number(true)
        set_ignore_classpath_errors(true) // gradle/build/resources/main does not exists, but it's not a problem
        set_output_format(Options.output_format_jimple)
        /**
         * In case of Java8, set_full_resolver(true) fails with "soot.SootResolver$SootClassNotFoundException:
         * couldn't find class: javax.crypto.BadPaddingException (is your soot-class-path set properly?)".
         * To cover that, set_allow_phantom_refs(true) is required
         */
        set_allow_phantom_refs(true) // Java8 related
        set_full_resolver(true)
    }

    Scene.v().loadNecessaryClasses()
    PackManager.v().runPacks()
    // we need this to create hierarchy of classes
}