/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package tests.properties.callPrivatePropertyFromGetProperties

import kotlinx.reflect.lite.*
import kotlinx.reflect.lite.full.*
import kotlinx.reflect.lite.jvm.*
import kotlinx.reflect.lite.tests.*
import kotlin.test.*

class K(private val value: String)

fun box(): String {
    val p = (K::class.java).kotlin.getMemberByName("value") as KProperty1<K, String>

    try {
        return p.get(K("Fail: private property should not be accessible by default"))
    }
    catch (e: IllegalAccessException) {
        assertEquals("Class kotlinx.reflect.lite.calls.CallerImpl\$FieldGetter can not access a member of class tests.properties.callPrivatePropertyFromGetProperties.K with modifiers \"private final\"", e.message)
        // OK
    }

    p.isAccessible = true

    return p.get(K("OK"))
}
