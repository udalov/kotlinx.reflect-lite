/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.reflect.lite.descriptors.impl

import kotlin.metadata.*
import kotlin.metadata.internal.common.*
import kotlin.metadata.jvm.*
import kotlinx.reflect.lite.builtins.*
import kotlinx.reflect.lite.descriptors.ClassDescriptor
import kotlinx.reflect.lite.descriptors.ModuleDescriptor
import kotlinx.reflect.lite.impl.*
import kotlinx.reflect.lite.jvm.*
import kotlinx.reflect.lite.misc.*
import kotlinx.reflect.lite.name.*

internal class ModuleDescriptorImpl(private val classLoader: ClassLoader) : ModuleDescriptor {
    override fun <T> findClass (name: ClassName): ClassDescriptor<T>? {
        val fqName = (JavaToKotlinClassMap.mapKotlinToJava(FqName(name.replace('/', '.'))) ?: ClassId(name)).asJavaLookupFqName()
        val jClass = when (fqName) { // todo why arrays are hardcoded
            "kotlin.BooleanArray" -> BooleanArray::class.java
            "kotlin.ByteArray" -> ByteArray::class.java
            "kotlin.CharArray" -> CharArray::class.java
            "kotlin.DoubleArray" -> DoubleArray::class.java
            "kotlin.FloatArray" -> FloatArray::class.java
            "kotlin.IntArray" -> IntArray::class.java
            "kotlin.LongArray" -> LongArray::class.java
            "kotlin.ShortArray" -> ShortArray::class.java
            "kotlin.Array" -> Array<Any>::class.java
            else -> classLoader.tryLoadClass(fqName)
        }
        @Suppress("UNCHECKED_CAST")
        return (jClass?.kotlin as KClassImpl<T>?)?.descriptor
    }
}
