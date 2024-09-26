/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.reflect.lite.descriptors.impl

import kotlinx.metadata.*
import kotlinx.reflect.lite.*
import kotlinx.reflect.lite.descriptors.*
import kotlinx.reflect.lite.descriptors.CallableDescriptor

internal interface AbstractCallableDescriptor : CallableDescriptor {
    val typeParameterTable: TypeParameterTable
}

internal fun Visibility.toKVisibility(): KVisibility? = when (this) {
    Visibility.INTERNAL -> KVisibility.INTERNAL
    Visibility.PRIVATE -> KVisibility.PRIVATE
    Visibility.PROTECTED -> KVisibility.PROTECTED
    Visibility.PUBLIC -> KVisibility.PUBLIC
    Visibility.PRIVATE_TO_THIS -> KVisibility.PRIVATE
    Visibility.LOCAL -> null
}
