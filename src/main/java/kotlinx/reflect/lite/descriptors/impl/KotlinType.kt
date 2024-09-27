/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.reflect.lite.descriptors.impl

import kotlin.metadata.*
import kotlinx.reflect.lite.KVariance
import kotlinx.reflect.lite.builtins.*
import kotlinx.reflect.lite.builtins.KotlinBuiltInsImpl
import kotlinx.reflect.lite.descriptors.*
import kotlinx.reflect.lite.descriptors.Annotated
import kotlinx.reflect.lite.descriptors.ClassDescriptor
import kotlinx.reflect.lite.descriptors.ClassifierDescriptor
import kotlinx.reflect.lite.descriptors.DeclarationDescriptor
import kotlinx.reflect.lite.descriptors.ModuleDescriptor
import kotlinx.reflect.lite.descriptors.TypeParameterDescriptor
import java.lang.reflect.*

internal class KotlinType(
    @JvmField val descriptor: ClassifierDescriptor,
    @JvmField val arguments: List<TypeProjection>,
    @JvmField val isMarkedNullable: Boolean // todo pass annotations
) : Annotated

internal fun KotlinType.isNullableType(): Boolean =
    isMarkedNullable || (descriptor is TypeParameterDescriptor && descriptor.upperBounds.any { it.isNullableType() })

internal class TypeParameterTable(
    @JvmField val typeParameters: List<TypeParameterDescriptorImpl>,
    private val parent: TypeParameterTable? = null
) {
    private fun getOrNull(id: Int): TypeParameterDescriptor? =
        typeParameters.find { it.id == id } ?: parent?.getOrNull(id)

    fun get(id: Int): TypeParameterDescriptor =
        getOrNull(id) ?: error("Unknown type parameter with id=$id")
}

internal class TypeProjection(
    val type: KotlinType,
    val isStarProjection: Boolean,
    val projectionKind: KVariance
)

internal fun KmType.toKotlinType(module: ModuleDescriptor, typeParameterTable: TypeParameterTable): KotlinType {
    val classifier = classifier.let { classifier ->
        when (classifier) {
            is KmClassifier.Class -> module.findClass<Any?>(classifier.name) ?: error ("Could not find class $classifier")
            is KmClassifier.TypeParameter -> typeParameterTable.get(classifier.id)
            is KmClassifier.TypeAlias -> TODO("Support KmClassifier.TypeAlias")
        }
    }
    return KotlinType(
        classifier,
        generateSequence(this, KmType::outerType).flatMap(KmType::arguments).map { (variance, type) ->
            TypeProjection(
                // if the projected type is null, then this is a star projection, use anyType
                type?.toKotlinType(module, typeParameterTable) ?: KotlinBuiltInsImpl.anyType,
                variance == null,
                variance?.toVariance() ?: KVariance.OUT
            )
        }.toList(),
        isNullable
    )
}

internal fun List<KmTypeParameter>.toTypeParameters(
    container: DeclarationDescriptor,
    module: ModuleDescriptor,
    parentTable: TypeParameterTable?
): TypeParameterTable {
    val list = ArrayList<TypeParameterDescriptorImpl>(size)
    return TypeParameterTable(list, parentTable).also { table ->
        mapTo(list) { TypeParameterDescriptorImpl(it, module, container, table) }
    }
}

internal val ClassifierDescriptor.kotlinType: KotlinType
    get() = KotlinType(
        this,
        (this as? ClassDescriptor<*>)?.typeParameters?.map {
            TypeProjection(it.kotlinType, false, KVariance.INVARIANT)
        }.orEmpty(),
        false
    )

internal val ClassifierDescriptor.defaultType: KotlinType
    get() = KotlinType(
        this,
        (this as? ClassDescriptor<*>)?.typeParameters?.map {
            TypeProjection(it.defaultType, false, KVariance.INVARIANT)
        }.orEmpty(),
        false
    )

// Copied from here: https://github.com/JetBrains/kotlin/blob/0a6d010d1c7258e25be8e4d99a44a5ab0e66ded2/core/reflection.jvm/src/kotlin/reflect/jvm/internal/util.kt#L260
internal fun defaultPrimitiveValue(type: Type?): Any? =
    if (type is Class<*> && type.isPrimitive) {
        when (type) {
            Boolean::class.java -> false
            Char::class.java -> 0.toChar()
            Byte::class.java -> 0.toByte()
            Short::class.java -> 0.toShort()
            Int::class.java -> 0
            Float::class.java -> 0f
            Long::class.java -> 0L
            Double::class.java -> 0.0
            String::class.java -> ""
            Void.TYPE -> throw IllegalStateException("Parameter with void type is illegal")
            else -> throw UnsupportedOperationException("Unknown primitive: $type")
        }
    } else null

private fun KmVariance.toVariance(): KVariance =
    when (this) {
        KmVariance.INVARIANT -> KVariance.INVARIANT
        KmVariance.IN -> KVariance.IN
        KmVariance.OUT -> KVariance.OUT
    }
