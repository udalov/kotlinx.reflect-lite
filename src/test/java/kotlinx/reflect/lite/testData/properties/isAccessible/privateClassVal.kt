/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package tests.properties.privateClassVal

import kotlinx.reflect.lite.*
import kotlinx.reflect.lite.full.*
import kotlinx.reflect.lite.jvm.*
import kotlinx.reflect.lite.tests.*
import kotlin.test.*

class Result {
    private val value = "OK"

    fun ref() = (Result::class.java).kotlin.getMemberByName("value") as KProperty1<Result, String>
}

fun box(): String {
    val p = Result().ref()
    try {
        p.get(Result())
        return "Fail: private property is accessible by default"
    } catch(e: IllegalAccessException) {
        assertEquals("Class kotlinx.reflect.lite.calls.CallerImpl\$FieldGetter can not access a member of class tests.properties.privateClassVal.Result with modifiers \"private final\"", e.message)
    }

    p.isAccessible = true

    val r = p.get(Result())

    p.isAccessible = false
    try {
        p.get(Result())
        return "Fail: setAccessible(false) had no effect"
    } catch(e: IllegalAccessException) {
        assertEquals("Class kotlinx.reflect.lite.calls.CallerImpl\$FieldGetter can not access a member of class tests.properties.privateClassVal.Result with modifiers \"private final\"", e.message)
    }

    return r
}
