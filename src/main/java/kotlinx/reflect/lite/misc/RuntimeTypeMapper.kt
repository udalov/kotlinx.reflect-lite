/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

// Most logic copied from: https://github.com/JetBrains/kotlin/blob/master/core/reflection.jvm/src/kotlin/reflect/jvm/internal/RuntimeTypeMapper.kt
package kotlinx.reflect.lite.misc

import kotlinx.metadata.jvm.*
import kotlinx.reflect.lite.builtins.*
import kotlinx.reflect.lite.descriptors.impl.*
import kotlinx.reflect.lite.name.*

internal sealed class JvmFunctionSignature {
    abstract fun asString(): String

    class KotlinFunction(private val signature: JvmMethodSignature) : JvmFunctionSignature() {
        private val _signature =   signature.toString()

        val methodName: String get() = signature.name
        val methodDesc: String get() = signature.descriptor

        override fun asString(): String = _signature
    }

    class KotlinConstructor(private val signature: JvmMethodSignature) : JvmFunctionSignature() {
        private val _signature =  /* compiled code */ signature.toString()

        val constructorDesc: String get() = signature.descriptor

        override fun asString(): String = _signature
    }
}

internal sealed class JvmPropertySignature {

    class KotlinProperty(
        private val descriptor: PropertyDescriptorImpl,
        val fieldSignature: JvmFieldSignature?,
        val getterSignature: JvmMethodSignature?,
        val setterSignature: JvmMethodSignature?
    ) : JvmPropertySignature()

}

internal object RuntimeTypeMapper {
    private val JAVA_LANG_VOID = ClassId.topLevel(FqName("java.lang.Void"))

    fun getKotlinBuiltInClassId(jClass: Class<*>): ClassId? {
        if (jClass.isArray) {
            jClass.componentType.primitiveType?.let {
                return ClassId(FqName("kotlin"), it.arrayTypeName)
            }
            return ClassId.topLevel(StandardNames.FqNames.array)
        }

        if (jClass == Void.TYPE) return JAVA_LANG_VOID

        jClass.primitiveType?.let {
            return ClassId(StandardNames.BUILT_INS_PACKAGE_FQ_NAME, it.typeName)
        }

        val classId = jClass.classId
        if (!classId.isLocal) {
            JavaToKotlinClassMap.mapJavaToKotlin(classId.asSingleFqName())?.let { return it }
        }
        return null
    }

    fun mapJvmClassToKotlinClassId(jClass: Class<*>): ClassId =
        getKotlinBuiltInClassId(jClass)?.let { return it } ?: jClass.classId

    private val Class<*>.primitiveType: PrimitiveType?
        get() = if (isPrimitive) JvmPrimitiveType.get(simpleName).primitiveType else null
}
