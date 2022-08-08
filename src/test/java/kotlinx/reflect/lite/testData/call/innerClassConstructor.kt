package tests.call.innerClassConstructor

import kotlinx.reflect.lite.*
import kotlinx.reflect.lite.impl.*
import kotlinx.reflect.lite.tests.*

class A {
    class Nested(val result: String)
    inner class Inner(val result: String)
}

fun box(): String {
    val nested =
        ((A::class.java).kotlinClass as KClass<A>).nestedClasses.single { it.simpleName == "Nested" } as KClass<A.Nested>
    val inner =
        ((A::class.java).kotlinClass as KClass<A>).nestedClasses.single { it.simpleName == "Inner" } as KClass<A.Inner>
    val aCons = ((A::class.java).kotlinClass as KClass<A>).getPrimaryConstructor()
    val nestedCons = nested.getPrimaryConstructor()
    val innerCons = inner.getPrimaryConstructor()
    return nestedCons.call("O").result + innerCons.call(aCons.call(), "K").result
}
