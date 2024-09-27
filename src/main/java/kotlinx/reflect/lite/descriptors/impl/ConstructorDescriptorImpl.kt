/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.reflect.lite.descriptors.impl

import kotlin.metadata.*
import kotlin.metadata.jvm.*
import kotlinx.reflect.lite.*
import kotlinx.reflect.lite.calls.*
import kotlinx.reflect.lite.calls.Caller
import kotlinx.reflect.lite.descriptors.*
import kotlinx.reflect.lite.misc.*
import kotlinx.reflect.lite.name.*
import java.lang.reflect.*

internal class ConstructorDescriptorImpl(
    private val kmCons: KmConstructor,
    override val module: ModuleDescriptor,
    override val containingClass: ClassDescriptor<*>, // todo they are always the same, remove containing class field
    override val container: ClassBasedDeclarationContainerDescriptor
) : AbstractFunctionDescriptor(), ConstructorDescriptor {

    override val name: Name
        get() = "<init>"

    override val modality: Modality
        get() = Modality.FINAL

    override val visibility: KVisibility?
        get() = kmCons.visibility.toKVisibility()

    override val isInline: Boolean
        get() = false

    override val isExternal: Boolean
        get() = false

    override val isOperator: Boolean
        get() = false

    override val isInfix: Boolean
        get() = false

    override val isSuspend: Boolean
        get() = false

    override val signature: JvmMethodSignature?
        get() = kmCons.signature

    override val isPrimary: Boolean
        get() = !kmCons.isSecondary

    override val isReal: Boolean
        get() = true

    override val isAnnotationConstructor: Boolean
        get() = false

    override val valueParameters: List<ValueParameterDescriptor>
        get() = kmCons.valueParameters.mapIndexed { index, parameter ->
            ValueParameterDescriptorImpl(parameter, this, index)
        }

    override val typeParameterTable: TypeParameterTable
        get() = emptyList<KmTypeParameter>().toTypeParameters(this, module, containingClass.typeParameterTable)

    override val typeParameters: List<TypeParameterDescriptor>
        get() = emptyList()

    override val dispatchReceiverParameter: ReceiverParameterDescriptor?
        get() = if (containingClass.isInner) containingClass.containingClass!!.thisAsReceiverParameter else null

    override val extensionReceiverParameter: ReceiverParameterDescriptor?
        get() = null

    override val returnType: KotlinType
        get() = containingClass.kotlinType

    private val jvmSignature: JvmFunctionSignature.KotlinConstructor by lazy {
        JvmFunctionSignature.KotlinConstructor(kmCons.signature ?: error("No constructor signature for ${kmCons}"))
    }

    override val member: Member? by lazy {
        container.findConstructorBySignature(jvmSignature.constructorDesc)
    }

    override val defaultMember: Member? by lazy {
        container.findDefaultConstructor(jvmSignature.constructorDesc)
    }

    override fun equals(other: Any?): Boolean {
        val that = (other as? FunctionDescriptor) ?: return false
        return container == that.container && name == that.name && signature == that.signature
    }

    override fun hashCode(): Int =
        (container.hashCode() * 31 + name.hashCode()) * 31 + signature.hashCode()
}

internal class JavaConstructorDescriptorImpl(
    private val cons: Constructor<*>,
    override val module: ModuleDescriptor,
    override val containingClass: ClassDescriptor<*>,
    override val container: ClassBasedDeclarationContainerDescriptor
) : ConstructorDescriptor {
    override val name: Name
        get() = "<init>"

    override val dispatchReceiverParameter: ReceiverParameterDescriptor? by lazy {
        if (containingClass.isInner) containingClass.containingClass!!.thisAsReceiverParameter else null
    }

    override val extensionReceiverParameter: ReceiverParameterDescriptor?
        get() = null

    override val valueParameters: List<ValueParameterDescriptor> by lazy {
        cons.genericParameterTypes.withIndex().map { (index, type) ->
            JavaValueParameterDescriptorImpl(this, index, type.javaToKotlinType(module))
        }
    }

    override val typeParameters: List<TypeParameterDescriptor> by lazy {
        cons.typeParameters.map {
            JavaTypeParameterDescriptorImpl(it, module, this)
        }
    }

    override val returnType: KotlinType
        get() = containingClass.kotlinType

    override val visibility: KVisibility?
        get() = TODO("Not yet implemented")

    override val isFinal: Boolean
        get() = Modifier.isFinal(cons.modifiers)

    override val isOpen: Boolean
        get() = !isFinal && !isAbstract

    override val isAbstract: Boolean
        get() = Modifier.isAbstract(cons.modifiers)

    override val isReal: Boolean
        get() = true

    override val isInline: Boolean
        get() = false

    override val isExternal: Boolean
        get() = false

    override val isOperator: Boolean
        get() = false

    override val isInfix: Boolean
        get() = false

    override val isSuspend: Boolean
        get() = false

    override val isAnnotationConstructor: Boolean
        get() = containingClass.jClass.isAnnotation

    override val caller: Caller<*>
        get() = TODO("Not yet implemented")

    override val defaultCaller: Caller<*>?
        get() = TODO("Not yet implemented")

    override val signature: JvmMethodSignature?
        get() = TODO("Not yet implemented")

    override val member: Member?
        get() = TODO("Not yet implemented")

    override val defaultMember: Member?
        get() = TODO("Not yet implemented")

    override val isPrimary: Boolean
        get() = TODO("Not yet implemented")
}
