/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package tests.functions.platformName

@JvmName("Fail")
fun OK() {}

fun box() = ::OK.name
