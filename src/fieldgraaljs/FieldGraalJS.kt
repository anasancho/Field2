package fieldgraaljs

import field.app.ThreadSync2
import field.linalg.Vec4
import field.utility.*
import fieldbox.boxes.Box
import fieldbox.boxes.Boxes
import fieldbox.boxes.Drawing
import fieldbox.boxes.plugins.IsExecuting
import fieldbox.execution.Completion
import fieldbox.execution.Execution
import fieldbox.io.IO
import fielded.Animatable
import fielded.DisabledRangeHelper
import fielded.RemoteEditor
import fielded.live.Asta
import fielded.plugins.Out
import fieldnashorn.Nashorn
import fieldnashorn.NashornExecution
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import java.io.OutputStream
import java.io.PrintWriter
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.regex.Pattern
import java.util.stream.Collectors
import kotlin.NoSuchElementException

class FieldGraalJS : Execution(null), IO.Loaded {

    val asta = Asta()

    val contexts = WeakHashMap<Box, Context>()


    override fun support(box: Box, prop: Dict.Prop<String>): Execution.ExecutionSupport? {
        if (box == this) return null
        if (prop != Execution.code) return null;

        val ef = this.properties.get(Execution.executionFilter)
        return if (ef == null || ef.apply(box)) wrap(box, prop) else null
    }

    private lateinit var output: Out

    init {


        Animatable.registerHandler { was, o ->
            if (o is java.util.function.Function<*, *>) {
                return@registerHandler object : Animatable.AnimationElement {

                    val stack = mutableListOf<java.util.function.Function<*, *>>(o as java.util.function.Function<*, *>)

                    override fun middle(isEnding: Boolean): Any? {
                        Nashorn.isEnding = isEnding

                        if (stack.size > 0) System.out.println(" stack is $stack")
                        else System.out.print("Z")

                        if (stack.size == 0) {
                            // ended, tell somebody we are no loner running ?
                            Nashorn.isEnding = true

                            return false

                        } else {

                            val oo = stack.removeAt(stack.size - 1)

                            var r = (oo as java.util.function.Function<Any?, Any?>).apply(null)
                            println("_r returns $r")

                            // insert special instructions here I suppose

                            if (r == null) // or undefined?
                            {
                                stack.add(oo)
                            } else if (r is Boolean && !r) {
                                // don't add it back
                            } else if (r is Boolean && r) {
                                stack.add(oo)
                            } else if (r is Map<*, *>) {

                                // function returned a generator

                                val n = r.get("next")
                                if (n != null && n is java.util.function.Function<*, *>) {
                                    stack.add(n)

                                    // then call again
                                    return middle(isEnding)
                                }
                                // generator returned a value
                                val v = r.get("value")
                                // which was a function, possibly a generator factory
                                if (v != null && v is java.util.function.Function<*, *>) {
                                    stack.add(v)
                                }


                                // and it's finished
                                val d = r.get("done")
                                if (d != null && d is Boolean && d) {
                                    // it's probably already gone now
                                    stack.remove(oo)
                                } else if (d != null && d is Boolean && !d) {
                                    stack.add(oo)
                                }

                               if (v is Map<*, *>)
                               {
                                   val n = v.get("next")
                                   if (n != null && n is java.util.function.Function<*, *>) {
                                       stack.add(n)

                                       // then call again
                                       return middle(isEnding)
                                   }
                               }

                                r = v

                            } else if (r is Number) {
                                if (r.toDouble() > 0) stack.add(oo)
                            }

                            r = interpretSpecialReturn(stack, r)

                            if (r is Until.Again) {
                                return middle(isEnding)
                            }

                        }


                        return this
                    }

                    override fun beginning(isEnding: Boolean): Any? {
//                        return middle(isEnding)
                        return this
                    }

                    override fun end(isEnding: Boolean): Any? {
//                        return middle(isEnding)
                        return this
                    }


                }
            }
            was
        }

    }

    private fun interpretSpecialReturn(stack: MutableList<Function<*, *>>, r: Any?): Any? {

        if (r is Until.Wait) {
            stack.add(java.util.function.Function<Any, Any> {
                if (r.condition()) {
                    false
                    //Until.Again()
                } else true
            })
            return r
        } else if (r is Until.Stop) {
            stack.clear()
            return Until.Again()
        }

        return r
    }

    private lateinit var root: Box

    override fun loaded() {
        output = this.find(Out.__out, this.both()).findFirst().orElseThrow({ IllegalStateException("Can't find html output support") })
        root = this.find(Boxes.root, this.both()).findFirst().orElseThrow({ IllegalStateException("Can't find root") })
    }


    val OUT_STREAM_HANDLE = object : ThreadLocal<((String) -> Unit)?>() {
        override fun initialValue(): ((String) -> Unit)? {
            return null
        }
    }

    val OUT_STREAM = object : OutputStream() {

        var c = StringBuffer()

        override fun write(b: Int) {

            c.appendCodePoint(b)
            if (c.endsWith("\n")) {
                OUT_STREAM_HANDLE.get()?.let {
                    it(c.toString())
                }
                print("(stdout) '" + c.toString() + "'")
                c.setLength(0)
            }
        }
    }

    val ERR_STREAM_HANDLE = object : ThreadLocal<((String) -> Unit)?>() {
        override fun initialValue(): ((String) -> Unit)? {
            return null
        }
    }

    val ERR_STREAM = object : OutputStream() {

        var c = StringBuffer()

        override fun write(b: Int) {

            c.appendCodePoint(b)
            if (c.endsWith("\n")) {
                ERR_STREAM_HANDLE.get()?.let {
                    it(c.toString())
                }
                print("(stderr) '" + c.toString() + "'")
                c.setLength(0)
            }
        }
    }

    var uniq = 0

    val supports = mutableMapOf<Box, Execution.ExecutionSupport>()

    private fun wrap(box: Box, prop: Dict.Prop<String>): Execution.ExecutionSupport {

        return supports.computeIfAbsent(box, {
            object : Execution.ExecutionSupport {

                var lineOffset = 0

                val prefix = "" + (uniq++)

                private var currentLineNumber: Triple<Box, Int, Boolean>? = null
                override fun executeTextFragment(textFragment: String, suffix: String, success: Consumer<String>, lineErrors: Consumer<Pair<Int, String>>): Any? {
                    return _executeTextFragment(textFragment, suffix, success, lineErrors)
                }

                fun _executeTextFragment(textFragment: String, suffix: String, success: Consumer<String>, lineErrors: Consumer<Pair<Int, String>>, hook: ((Any?) -> Unit)? = null): Any? {
                    var textFragment = textFragment
                    RemoteEditor.removeBoxFeedback(Optional.of(box), "__redmark__")

                    try {
                        Execution.context.get().push(box)

                        try {
                            textFragment = asta.transformer(box).transform(textFragment, false).first
                        } catch (e: Exception) {
                            System.out.println(" -- ASTA didn't succeed, usually a syntax error of some kind")
                            e.printStackTrace()
                        }


                        for (i in 0 until lineOffset) textFragment = "\n" + textFragment

                        try {

                            var hadOutput = false

                            currentLineNumber = null
                            output.setWriter(PrintWriter(OUT_STREAM), this::setCurrentLineNumberForPrinting)

                            OUT_STREAM_HANDLE.set {
                                hadOutput = true

                                val c = currentLineNumber

                                if (c == null || c.first == null || c.second == -1) {
                                    //								success.accept(s);
                                    val o = box.find(Execution.directedOutput, box.upwards()).collect(Collectors.toSet())
                                    o.forEach { x -> x.accept(Quad(box, -1, it, true)) }

                                } else {

                                    val o = box.find(Execution.directedOutput, box.upwards()).collect(Collectors.toSet())

                                    if (o.size > 0) {
                                        o.forEach { x ->
                                            x.accept(Quad(c.first, c.second, it, c.third))
                                        }
                                    } else {
                                        //									success.accept(finalS);

                                        o.forEach { x -> x.accept(Quad(box, -1, it, true)) }

                                    }
                                }

                            }
                            ERR_STREAM_HANDLE.set {
                                hadOutput = true
                                lineErrors.accept(Pair(-1, it))
                            }


                            println("text fragment is\n$textFragment")
                            val ret = actuallyExecute(textFragment, box)

                            hook?.invoke(ret)

                            if (!hadOutput && ret != null) {
                                OUT_STREAM_HANDLE.get()?.invoke(ret.toString()) // todo: rich formatting
                            } else if (!hadOutput) {
                                success.accept(" &#10003; ")
                            }
                            RemoteEditor.boxFeedback(Optional.of(box), Vec4(0.3, 0.7, 0.3, 0.5))
                        } catch (e: Throwable) {
                            RemoteEditor.boxFeedback(Optional.of(box), Vec4(1.0, 0.0, 0.0, 0.5), "__redmark__", -1, -1)

                            System.out.println(" inside our custom handler for errors in JS")

                            e.printStackTrace()

                            // todo, parse stacktrace dispatch to correct box

                            val p = Pattern.compile("bx\\[(.*?)\\]/(.*?).js")

                            try {
                                var boxid: String? = null
                                var lineNumber = -1
                                e.stackTrace.first {
                                    val m = p.matcher(it.fileName)

                                    System.out.println(" looking at ${it.fileName}")

                                    if (m.find()) {
                                        boxid = m.group(2)
                                        lineNumber = it.lineNumber
                                        System.out.println(" found line number $lineNumber")
                                        true
                                    } else false
                                }

                                if (lineNumber != -1) {
                                    lineErrors.accept(Pair(lineNumber, e.message ?: e.javaClass.simpleName))
                                } else lineErrors.accept(Pair(-1, e.message!!))
                            } catch (e2: NoSuchElementException) {
                                System.out.println(" couldn't pull stacktrace out of exception, parsing message instead")
                                // couldn't get stacktrace? test the message instead
                                val p2 = Pattern.compile("bx\\[(.*?)\\]/(.*?).js:(.*?):")

                                var m = p2.matcher(e.message)
                                if (m.find()) {
                                    val boxid = m.group(2)
                                    val lineNumber = m.group(3).toInt()
                                    lineErrors.accept(Pair(lineNumber, e.message ?: e.javaClass.simpleName))
                                } else {
                                    lineErrors.accept(Pair(-1, e.message ?: e.javaClass.simpleName))
                                }
                            }

                        }

                        return null
                    } finally {
                        Execution.context.get().pop()
                    }
                }


                private fun setCurrentLineNumberForPrinting(boxLine: Triple<Box, Int, Boolean>) {
                    currentLineNumber = boxLine
                }

                var filename = "unnamed"

                private fun target(box: Box): String? {
                    return filename;
                }

                private fun actuallyExecute(textFragment: String, box: Box): Any? {

                    // palm off on custom executor? needed for audio, no?
                    // threadsync stuff? No longer needed at all with explicit yield?

                    val context = contexts.computeIfAbsent(box, {
                        val c = Context.newBuilder("js").allowAllAccess(true).err(ERR_STREAM).out(OUT_STREAM).logHandler(OUT_STREAM).build()

                        val bindings = c.getBindings("js")
                        configureInitialBindings(bindings, box, c)

                        c
                    })

                    context.enter()
                    try {

                        // cache this?
                        val source = (Source.newBuilder("js", textFragment, target(box) + ".js")).build()
                        val v = context.eval(source)

                        if (v != null) {
                            return v.`as`(Any::class.java)
                        }

                    } finally {
                        context.leave()
                    }

                    return null
                }

                fun configureInitialBindings(bindings: org.graalvm.polyglot.Value, box: fieldbox.boxes.Box, c: org.graalvm.polyglot.Context) {
                    bindings.putMember("_", box)
                    bindings.putMember("__", root)
                    c.eval("js", Nashorn._sstdlib)
                }


                override fun getBinding(name: String): Any? {
                    return null
                }

                override fun executeAll(allText: String, lineErrors: Consumer<Pair<Int, String>>, success: Consumer<String>) {
                    executeTextFragment(allText, "", success, lineErrors)
                }

                override fun begin(lineErrors: Consumer<Pair<Int, String>>, success: Consumer<String>, initiator: Map<String, Any>, endOthers: Boolean): String? {
                    if (endOthers) end(lineErrors, success);

                    // needed?
                    lineOffset = 0


                    val code = DisabledRangeHelper.getStringWithDisabledRanges(box, Execution.code, "/* -- start -- ", "-- end -- */")

                    var returnName: String? = null

                    _executeTextFragment(code, "begin", success, lineErrors) {

                        var _r: Any? = contexts[box]!!.getBindings("js").getMember("_r")

                        if (_r is Value) _r = _r.`as`(Any::class.java)

                        val r = interpretAnimation(_r)
                        if (r != null) {
                            if (endOthers) end(lineErrors, success)

                            val name = prefixFor(box) + "_animator" + prefix + "_" + uniq
                            box.properties.putToMap<String, Supplier<Boolean>>(Boxes.insideRunLoop, name, r)
                            box.first(IsExecuting.isExecuting).ifPresent { x -> x.accept(box, name) }

                            returnName = name
                            uniq++
                        }


                    }

                    return returnName

                }

                private fun prefixFor(box: Box): String {
                    return box.first<ExecutorService>(NashornExecution.customExecutor).map<String> { e ->
                        if (e is ThreadSync2.TrappedExecutorName) return@map e.executionNamePrefix()
                        else return@map "main."
                    }.filter(Predicate<String> { Objects.nonNull(it) }).orElse("main.")
                }

                override fun end(lineErrors: Consumer<field.utility.Pair<Int, String>>, success: Consumer<String>) {
                    end(Pattern.quote(prefixFor(box)) + "_animator" + prefix + "_.*", lineErrors, success)
                }

                override fun end(regex: String?, lineErrors: Consumer<field.utility.Pair<Int, String>>, success: Consumer<String>) {
                    RemoteEditor.removeBoxFeedback(Optional.of(box), "__redmark__")
                    val m = box.properties.get(Boxes.insideRunLoop) ?: return


                    val p = Pattern.compile(regex!!)

                    for (s in ArrayList(m.keys)) {
                        if (p.matcher(s).matches()) {
                            val b = m[s]
                            if (b is Consumer<*>) (b as Consumer<Boolean>).accept(false)
                            else {
                                m.remove(s)
                            }
                        }
                    }

                    Drawing.dirty(box)
                }


                private fun interpretAnimation(r: Any?): Supplier<Boolean>? {
                    if (r is Supplier<*> && r is Consumer<*>) return r as Supplier<Boolean>
                    val res = Animatable.interpret(r, null) ?: return null
                    return Animatable.Shim(res)
                }


                override fun setConsoleOutput(stdout: Consumer<String>, stderr: Consumer<String>) {

                    // todo!?

                }

                override fun completion(allText: String, line: Int, ch: Int, results: Consumer<List<Completion>>, explicit: Boolean) {


                    // todo!

                }

                override fun imports(allText: String, line: Int, ch: Int, results: Consumer<List<Completion>>) {

                    // todo!

                }

                override fun getCodeMirrorLanguageName(): String {
                    return "javascript"
                }

                override fun getDefaultFileExtension(): String {
                    return ".js"
                }

                var prop: Dict.Prop<String>? = null


                override fun setFilenameForStacktraces(name: String) {
                    filename = name
                }

                override fun setLineOffsetForFragment(lineOffset: Int, origin: Dict.Prop<String>) {
                    this.lineOffset = lineOffset
                    this.prop = origin
                }
            }
        })
    }
}

