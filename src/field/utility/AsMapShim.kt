package field.utility

import fieldlinker.AsMap
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyObject

class AsMapShim(val target : AsMap, val getKeys : (AsMap) -> Any, val customToString : (AsMap) -> String) : ProxyObject
{
    override fun getMember(key: String): Any? {
        return target.asMap_get(key)
    }

    override fun getMemberKeys(): Any {
        return getKeys(target)
    }

    override fun hasMember(key: String): Boolean {
        return target.asMap_isProperty(key)
    }

    override fun putMember(key: String, value: Value) {
        target.asMap_set(key, value.`as`(Any::class.java))
    }

    override fun removeMember(key: String?): Boolean {
        return target.asMap_delete(key)
    }

    override fun toString(): String {
        return customToString(target)
    }
}