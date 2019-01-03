package field.utility

import org.graalvm.polyglot.proxy.ProxyExecutable
import java.lang.ClassCastException
import java.lang.NullPointerException
import java.lang.reflect.Method

class GetMemberHelper(val inside: Any) {
    val cache = mutableMapOf<String, ProxyExecutable>()

    companion object {
        val methodCache = mutableMapOf<Class<*>, MutableMap<String, MutableList<kotlin.Pair<Method, List<Class<*>>>>>>()
    }

    val methods = methodCache.computeIfAbsent(inside.javaClass, {
        computeMethodList(it)
    })

    private fun computeMethodList(it: Class<*>): MutableMap<String, MutableList<kotlin.Pair<Method, List<Class<*>>>>> {
        val m = mutableMapOf<String, MutableList<kotlin.Pair<Method, List<Class<*>>>>>()

        it.methods.forEach {
            m.computeIfAbsent(it.name, { mutableListOf() }).add(it to debox(it.parameterTypes.toList()))
        }

        return m
    }

    private fun debox(toList: List<Class<*>>): List<Class<*>> {

        return toList.map {
            if (it.isPrimitive) {
                toWrapper(it)
            } else it
        }

    }

    fun toWrapper(clazz: Class<*>): Class<*> {
        if (!clazz.isPrimitive) return clazz

        if (clazz == Integer.TYPE) return java.lang.Integer::class.java
        if (clazz == java.lang.Long.TYPE) return java.lang.Long::class.java
        if (clazz == java.lang.Boolean.TYPE) return java.lang.Boolean::class.java
        if (clazz == java.lang.Byte.TYPE) return java.lang.Byte::class.java
        if (clazz == Character.TYPE) return java.lang.Character::class.java
        if (clazz == java.lang.Float.TYPE) return java.lang.Float::class.java
        if (clazz == java.lang.Double.TYPE) return java.lang.Double::class.java
        if (clazz == java.lang.Short.TYPE) return java.lang.Short::class.java
        return if (clazz == Void.TYPE) Void::class.java else clazz

    }

    fun get(name: String): ProxyExecutable {
        return cache.computeIfAbsent(name, {
            proxyFor(inside, name)
        })
    }

    fun has(name: String): Boolean {
        return methods.containsKey(name)
    }

    private fun proxyFor(inside: Any, name: String): ProxyExecutable {

        val m = methods.get(name)
        if (m == null) throw NullPointerException(" can't find $name to call in $inside")

        if (m.size > 1) return ProxyExecutable { args ->

            val a2 = Array<Any?>(args.size) { null }
            val c2 = Array<Class<*>?>(args.size) { null }
            for (i in 0 until args.size) {
                a2[i] = if (args[i].isNumber) args[i].asDouble() else args[i].`as`(Any::class.java)
                c2[i] = a2[i]?.javaClass
            }

            for (i in 0 until m.size) {
                if (m[i].second.size != a2.size) continue
                if (isCompatible(m[i].second, a2, c2)) {
                    val r = m[i].first.invoke(inside, *a2)
                    return@ProxyExecutable r
                }
            }

            throw ClassCastException(" can't figure out which of ${m.size} versions of ${name} to call given these arguments ${a2.toList()} / ${c2.toList()}")
        }
        else return ProxyExecutable { args ->

            val a2 = Array<Any?>(args.size) { null }
            for (i in 0 until args.size) {
                a2[i] = args[i].`as`(Any::class.java)
            }

            val r = m[0].first.invoke(inside, *a2)
            return@ProxyExecutable r
        }

    }

    private fun isCompatible(targets: List<Class<*>>, values: Array<Any?>, valueTypes: Array<Class<*>?>): Boolean {
        for (i in 0 until targets.size) {
            if (!targets[i].isAssignableFrom(valueTypes[i])) return false
        }
        return true
    }
}