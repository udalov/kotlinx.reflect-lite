/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.reflect.lite.descriptors

import kotlinx.metadata.*
import kotlinx.reflect.lite.descriptors.impl.*
import kotlinx.reflect.lite.name.*

internal class ValueParameterDescriptorImpl(
    private val kmValueParam: KmValueParameter,
    override val containingDeclaration: AbstractFunctionDescriptor,
    private val index: Int
) : ValueParameterDescriptor {
    override val name: Name
        get() = kmValueParam.name

    override val type: KotlinType
        get() = kmValueParam.type.toKotlinType(containingDeclaration.module, containingDeclaration.typeParameterTable)

    override val declaresDefaultValue: Boolean
        get() = kmValueParam.declaresDefaultValue

    override val varargElementType: KotlinType?
        get() = kmValueParam.varargElementType?.toKotlinType(containingDeclaration.module, containingDeclaration.typeParameterTable)
}

internal class JavaValueParameterDescriptorImpl(
    override val containingDeclaration: CallableDescriptor,
    private val index: Int,
    override val type: KotlinType,
) : ValueParameterDescriptor {
    override val name: Name
        get() = "p$index"

    override val declaresDefaultValue: Boolean
        get() = false
    override val varargElementType: KotlinType?
        get() = null
}

internal class PropertySetterParameterDescriptor(
    private val kmSetterParam: KmValueParameter?,
    private val setter: PropertySetterDescriptorImpl,
) : ValueParameterDescriptor {
    override val name: Name
        get() = kmSetterParam?.name ?: "<set-?>"

    override val containingDeclaration: CallableDescriptor
        get() = setter
    override val type: KotlinType
        get() = setter.property.returnType
    override val declaresDefaultValue: Boolean
        get() = false
    override val varargElementType: KotlinType?
        get() = null
}
