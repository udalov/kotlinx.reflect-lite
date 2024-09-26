/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.reflect.lite.descriptors.impl

import kotlinx.metadata.*
import kotlinx.reflect.lite.*
import kotlinx.reflect.lite.descriptors.*
import kotlinx.reflect.lite.descriptors.DeclarationDescriptor
import kotlinx.reflect.lite.descriptors.ModuleDescriptor
import kotlinx.reflect.lite.descriptors.TypeParameterDescriptor
import kotlinx.reflect.lite.name.*
import java.lang.reflect.*

internal class TypeParameterDescriptorImpl(
    private val kmTypeParam: KmTypeParameter,
    private val module: ModuleDescriptor,
    override val containingDeclaration: DeclarationDescriptor,
    private val typeParameterTable: TypeParameterTable
) : TypeParameterDescriptor {
    internal val id: Int get() = kmTypeParam.id

    override val name: Name
        get() = kmTypeParam.name

    override val isReified: Boolean
        get() = kmTypeParam.isReified

    override val upperBounds: List<KotlinType>
        get() = kmTypeParam.upperBounds.map { it.toKotlinType(module, typeParameterTable) }

    override val variance: KVariance
        get() = kmTypeParam.variance.toVariance()
}

internal class JavaTypeParameterDescriptorImpl(
    private val typeVariable: TypeVariable<*>,
    private val module: ModuleDescriptor,
    override val containingDeclaration: DeclarationDescriptor
) : TypeParameterDescriptor {
    override val name: Name
        get() = typeVariable.name

    override val upperBounds: List<KotlinType>
        get() = typeVariable.bounds.map { it.javaToKotlinType(module) }

    override val variance: KVariance
        get() = KVariance.INVARIANT
    override val isReified: Boolean
        get() = false

    override fun equals(other: Any?): Boolean =
        other is TypeParameterDescriptor && name == other.name && containingDeclaration == other.containingDeclaration

    override fun hashCode(): Int =
        name.hashCode() * 31 + containingDeclaration.hashCode()
}
