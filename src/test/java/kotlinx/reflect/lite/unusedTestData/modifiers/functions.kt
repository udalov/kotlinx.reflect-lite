/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package tests.modifiers.functions

import kotlin.test.assertTrue
import kotlin.test.assertFalse

inline fun inline() {}
class External { external fun external() }
operator fun Unit.invoke() {}
infix fun Unit.infix(unit: Unit) {}
class Suspend { suspend fun suspend() {} }

val externalGetter = Unit
    external get

inline var inlineProperty: Unit
    get() = Unit
    set(value) {}

fun box(): String {
    assertTrue(::inline.isInline)
    assertFalse(::inline.isExternal)
    assertFalse(::inline.isOperator)
    assertFalse(::inline.isInfix)
    assertFalse(::inline.isSuspend)

    assertFalse(External::external.isInline)
    assertTrue(External::external.isExternal)
    assertFalse(External::external.isOperator)
    assertFalse(External::external.isInfix)
    assertFalse(External::external.isSuspend)

    assertFalse(Unit::invoke.isInline)
    assertFalse(Unit::invoke.isExternal)
    assertTrue(Unit::invoke.isOperator)
    assertFalse(Unit::invoke.isInfix)
    assertFalse(Unit::invoke.isSuspend)

    assertFalse(Unit::infix.isInline)
    assertFalse(Unit::infix.isExternal)
    assertFalse(Unit::infix.isOperator)
    assertTrue(Unit::infix.isInfix)
    assertFalse(Unit::infix.isSuspend)

    assertFalse(Suspend::suspend.isInline)
    assertFalse(Suspend::suspend.isExternal)
    assertFalse(Suspend::suspend.isOperator)
    assertFalse(Suspend::suspend.isInfix)
    assertTrue(Suspend::suspend.isSuspend)

    assertTrue(::externalGetter.getter.isExternal)
    assertFalse(::externalGetter.getter.isInline)

    assertFalse(::inlineProperty.getter.isExternal)
    assertTrue(::inlineProperty.getter.isInline)
    assertTrue(::inlineProperty.setter.isInline)
    assertFalse(::inlineProperty.isSuspend)

    return "OK"
}
