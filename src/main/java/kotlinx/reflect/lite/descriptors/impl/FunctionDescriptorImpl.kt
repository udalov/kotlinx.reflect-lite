/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.reflect.lite.descriptors.impl

import kotlinx.metadata.*
import kotlinx.metadata.jvm.*
import kotlinx.reflect.lite.*
import kotlinx.reflect.lite.calls.*
import kotlinx.reflect.lite.descriptors.*
import kotlinx.reflect.lite.impl.KotlinReflectionInternalError
import kotlinx.reflect.lite.name.*
import java.lang.reflect.*
import kotlinx.reflect.lite.misc.JvmFunctionSignature

internal abstract class AbstractFunctionDescriptor : AbstractCallableDescriptor, FunctionDescriptor {

    abstract val modality: Modality

    override val isFinal: Boolean
        get() = modality == Modality.FINAL

    override val isOpen: Boolean
        get() = modality == Modality.OPEN

    override val isAbstract: Boolean
        get() = modality == Modality.ABSTRACT

    override val isReal: Boolean
        get() = true

    override val isAnnotationConstructor: Boolean
        get() = name == "<init>" && container.jClass.isAnnotation

    override val caller: Caller<*> by lazy {
        createCaller(member)
    }

    override val defaultCaller: Caller<*>? by lazy {
        defaultMember?.let { createDefaultCaller(it) }
    }

    private fun createDefaultCaller(defaultMember: Member) =
        when (defaultMember) {
            is Constructor<*> ->
                createConstructorCaller(defaultMember)
            is Method -> when {
                // Note that static $default methods for @JvmStatic functions are generated differently in objects and companion objects.
                // In objects, $default's signature does _not_ contain the additional object instance parameter,
                // as opposed to companion objects where the first parameter is the companion object instance.
                // TODO implement Annotated
                (this.member as Method).declaredAnnotations.find { it.annotationClass.java.name == "kotlin.jvm.JvmStatic" } != null &&
                containingClass?.isCompanion == false ->
                    createJvmStaticInObjectCaller(defaultMember)
                else ->
                    createStaticMethodCaller(defaultMember)
            }
            else -> null
        }

    private fun createCaller(member: Member?) =
        when (member) {
            is Constructor<*> ->
                createConstructorCaller(member)
            is Method -> when {
                !Modifier.isStatic(member.modifiers) ->
                    createInstanceMethodCaller(member)
                containingClass?.isCompanion == false ->
                    createJvmStaticInObjectCaller(member)
                else ->
                    createStaticMethodCaller(member)
            }
            else -> throw KotlinReflectionInternalError("Could not compute caller for function: $this (member = $member)")
        }

    private fun createConstructorCaller(member: Constructor<*>): CallerImpl<Constructor<*>> {
        return CallerImpl.Constructor(member)
    }

    private fun createInstanceMethodCaller(member: Method): CallerImpl<Method> {
        return CallerImpl.Method.Instance(member)
    }

    private fun createStaticMethodCaller(member: Method): CallerImpl<Method> {
        return CallerImpl.Method.Static(member)
    }

    private fun createJvmStaticInObjectCaller(member: Method): CallerImpl<Method> {
        return CallerImpl.Method.JvmStaticInObject(member)
    }
}

internal class FunctionDescriptorImpl(
    private val kmFunction: KmFunction,
    override val module: ModuleDescriptor,
    override val containingClass: ClassDescriptor<*>?,
    override val container: ClassBasedDeclarationContainerDescriptor
) : AbstractFunctionDescriptor() {

    override val name: Name
        get() = kmFunction.name

    override val modality: Modality
        get() = kmFunction.modality

    override val visibility: KVisibility?
        get() = kmFunction.visibility.toKVisibility()

    override val isInline: Boolean
        get() = kmFunction.isInline

    override val isExternal: Boolean
        get() = kmFunction.isExternal

    override val isOperator: Boolean
        get() = kmFunction.isOperator

    override val isInfix: Boolean
        get() = kmFunction.isInfix

    override val isSuspend: Boolean
        get() = kmFunction.isSuspend

    override val signature: JvmMethodSignature? by lazy { kmFunction.signature }

    override val valueParameters: List<ValueParameterDescriptor> by lazy {
        kmFunction.valueParameters.mapIndexed { index, kmValueParam ->
            ValueParameterDescriptorImpl(kmValueParam, this, index)
        }
    }

    override val typeParameterTable: TypeParameterTable =
        kmFunction.typeParameters.toTypeParameters(this, module, containingClass?.typeParameterTable)

    override val typeParameters: List<TypeParameterDescriptor>
        get() = typeParameterTable.typeParameters

    override val dispatchReceiverParameter: ReceiverParameterDescriptor?
        get() = containingClass?.thisAsReceiverParameter

    override val extensionReceiverParameter: ReceiverParameterDescriptor?
        get() = kmFunction.receiverParameterType?.let {
            ReceiverParameterDescriptorImpl(it.toKotlinType(module, typeParameterTable))
        }

    override val returnType: KotlinType
        get() = kmFunction.returnType.toKotlinType(module, typeParameterTable)

    private val jvmSignature: JvmFunctionSignature.KotlinFunction by lazy {
        JvmFunctionSignature.KotlinFunction(kmFunction.signature ?: error("No signature for ${kmFunction}"))
    }

    override val member: Member? by lazy {
        container.findMethodBySignature(jvmSignature.methodName, jvmSignature.methodDesc)
    }

    override val defaultMember: Member? by lazy {
        container.findDefaultMethod(jvmSignature.methodName, jvmSignature.methodDesc, !Modifier.isStatic(member!!.modifiers))
    }

    override fun equals(other: Any?): Boolean {
        val that = (other as? FunctionDescriptor) ?: return false
        return container == that.container && name == that.name && signature == that.signature
    }

    override fun hashCode(): Int =
        (container.hashCode() * 31 + name.hashCode()) * 31 + signature.hashCode()
}

internal class JavaFunctionDescriptorImpl(
    val method: Method,
    override val module: ModuleDescriptor,
    override val containingClass: JavaClassDescriptor<*>
): FunctionDescriptor {
    override val name: Name
        get() = TODO("Not yet implemented")

    override val container: ClassBasedDeclarationContainerDescriptor
        get() = TODO("Not yet implemented")

    override val dispatchReceiverParameter: ReceiverParameterDescriptor?
        get() = containingClass.thisAsReceiverParameter

    override val extensionReceiverParameter: ReceiverParameterDescriptor?
        get() = null

    override val valueParameters: List<ValueParameterDescriptor>
        get() = method.genericParameterTypes.withIndex().map { (index, type) ->
            JavaValueParameterDescriptorImpl(this, index, type.javaToKotlinType(module))
        }

    override val typeParameters: List<TypeParameterDescriptor>
        get() = method.typeParameters.map {
            JavaTypeParameterDescriptorImpl(it, module, this)
        }
    override val returnType: KotlinType
        get() = method.genericReturnType.javaToKotlinType(module)

    override val visibility: KVisibility?
        get() = TODO("Not yet implemented")
    override val isFinal: Boolean
        get() = TODO("Not yet implemented")
    override val isOpen: Boolean
        get() = TODO("Not yet implemented")
    override val isAbstract: Boolean
        get() = TODO("Not yet implemented")
    override val isReal: Boolean
        get() = TODO("Not yet implemented")
    override val caller: Caller<*>
        get() = TODO("Not yet implemented")
    override val defaultCaller: Caller<*>?
        get() = TODO("Not yet implemented")
    override val isInline: Boolean
        get() = TODO("Not yet implemented")
    override val isExternal: Boolean
        get() = TODO("Not yet implemented")
    override val isOperator: Boolean
        get() = TODO("Not yet implemented")
    override val isInfix: Boolean
        get() = TODO("Not yet implemented")
    override val isSuspend: Boolean
        get() = TODO("Not yet implemented")
    override val isAnnotationConstructor: Boolean
        get() = TODO("Not yet implemented")
    override val signature: JvmMethodSignature?
        get() = TODO("Not yet implemented")
    override val member: Member?
        get() = TODO("Not yet implemented")
    override val defaultMember: Member?
        get() = TODO("Not yet implemented")
}
