/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.reflect.lite.descriptors.impl

import kotlin.metadata.*
import kotlin.metadata.jvm.*
import kotlinx.reflect.lite.KVisibility
import kotlinx.reflect.lite.calls.*
import kotlinx.reflect.lite.calls.Caller
import kotlinx.reflect.lite.descriptors.*
import kotlinx.reflect.lite.impl.KotlinReflectionInternalError
import kotlinx.reflect.lite.misc.JvmPropertySignature
import kotlinx.reflect.lite.name.*
import java.lang.reflect.*

internal class PropertyDescriptorImpl(
    val kmProperty: KmProperty,
    override val module: ModuleDescriptor,
    override val containingClass: ClassDescriptor<*>?,
    override val container: ClassBasedDeclarationContainerDescriptor
) : AbstractCallableDescriptor, PropertyDescriptor {
    override val name: Name
        get() = kmProperty.name

    override val visibility: KVisibility?
        get() = kmProperty.visibility.toKVisibility()

    override val isFinal: Boolean
        get() = kmProperty.modality == Modality.FINAL
    override val isOpen: Boolean
        get() = kmProperty.modality == Modality.OPEN
    override val isAbstract: Boolean
        get() = kmProperty.modality == Modality.ABSTRACT

    override val valueParameters: List<ValueParameterDescriptor>
        get() = emptyList()

    override val typeParameterTable: TypeParameterTable =
        kmProperty.typeParameters.toTypeParameters(this, module, containingClass?.typeParameterTable)

    override val typeParameters: List<TypeParameterDescriptor>
        get() = typeParameterTable.typeParameters

    override val returnType: KotlinType
        get() = kmProperty.returnType.toKotlinType(module, typeParameterTable)

    override val isConst: Boolean
        get() = kmProperty.isConst

    override val isLateInit: Boolean
        get() = kmProperty.isLateinit

    override val isVar: Boolean
        get() = kmProperty.isVar

    override val isReal: Boolean
        get() = true

    override val isMovedFromInterfaceCompanion: Boolean
        get() = kmProperty.isMovedFromInterfaceCompanion

    override val dispatchReceiverParameter: ReceiverParameterDescriptor?
        get() = containingClass?.thisAsReceiverParameter

    override val extensionReceiverParameter: ReceiverParameterDescriptor?
        get() = kmProperty.receiverParameterType?.let {
            ReceiverParameterDescriptorImpl(it.toKotlinType(module, typeParameterTable))
        }

    override val signature: JvmFieldSignature?
        get() = kmProperty.fieldSignature

    override val jvmSignature: JvmPropertySignature.KotlinProperty by lazy {
        JvmPropertySignature.KotlinProperty(
            this,
            kmProperty.fieldSignature,
            kmProperty.getterSignature,
            kmProperty.setterSignature
        )
    }

    // Logic from: https://github.com/JetBrains/kotlin/blob/3b5179686eaba0a71bcca53c2cc922a54cc9241f/core/reflection.jvm/src/kotlin/reflect/jvm/internal/KPropertyImpl.kt#L51
    override val javaField: Field? by lazy {
        jvmSignature.fieldSignature?.let {
            val owner = if (isMovedFromInterfaceCompanion) {
                container.jClass.enclosingClass
            } else {
                containingClass?.jClass ?: container.jClass
            }
            try {
                owner.getDeclaredField(it.name)
            } catch (e: NoSuchFieldException) {
                null
            }
        }
    }

    override val getter: PropertyGetterDescriptor? by lazy { PropertyGetterDescriptorImpl(this) }

    override val setter: PropertySetterDescriptor? by lazy {
        if (kmProperty.setter != null) PropertySetterDescriptorImpl(this) else null
    }

    override val caller: Caller<*> by lazy {
        getter?.caller ?: error("The property has no getter")
    }

    override val defaultCaller: Caller<*> by lazy {
        getter?.defaultCaller ?: error("The property has no getter")
    }

    override fun equals(other: Any?): Boolean {
        val that = (other as? PropertyDescriptor) ?: return false
        return container == that.container && name == that.name && signature == that.signature
    }

    override fun hashCode(): Int =
        (container.hashCode() * 31 + name.hashCode()) * 31 + signature.hashCode()
}

internal abstract class PropertyAccessorDescriptorImpl(
    override val property: PropertyDescriptorImpl
) : AbstractFunctionDescriptor(), PropertyAccessorDescriptor {
    override val module: ModuleDescriptor
        get() = property.module
    override val containingClass: ClassDescriptor<*>?
        get() = property.containingClass
    override val container: ClassBasedDeclarationContainerDescriptor
        get() = property.container

    override val typeParameterTable: TypeParameterTable
        get() = property.typeParameterTable
    override val typeParameters: List<TypeParameterDescriptor>
        get() = property.typeParameters
    override val returnType: KotlinType
        get() = property.returnType

    override val dispatchReceiverParameter: ReceiverParameterDescriptor?
        get() = property.dispatchReceiverParameter
    override val extensionReceiverParameter: ReceiverParameterDescriptor?
        get() = property.extensionReceiverParameter

    override val isReal: Boolean
        get() = property.isReal

    abstract override val member: Method?

    override val defaultMember: Member?
        get() = null

    override val caller: Caller<*> by lazy {
        val accessor = member
        when {
            accessor == null -> {
                val javaField = property.javaField
                    ?: throw KotlinReflectionInternalError("No accessors or field is found for property $property")
                computeFieldCaller(javaField)
            }
            !Modifier.isStatic(accessor.modifiers) ->
                CallerImpl.Method.Instance(accessor)
            isJvmStaticProperty() ->
                CallerImpl.Method.JvmStaticInObject(accessor)
            else ->
                CallerImpl.Method.Static(accessor)
        }
    }

    protected abstract fun computeFieldCaller(field: Field): Caller<*>

    protected fun isJvmStaticProperty(): Boolean {
        val annotationsMethodSignature = property.kmProperty.syntheticMethodForAnnotations ?: return false
        val annotationsMethod = property.container.jClass.declaredMethods.single { it.name == annotationsMethodSignature.name }
        return annotationsMethod.getDeclaredAnnotation(JvmStatic::class.java) != null
    }

    // Logic from: https://github.com/JetBrains/kotlin/blob/3b5179686eaba0a71bcca53c2cc922a54cc9241f/core/reflection.jvm/src/kotlin/reflect/jvm/internal/KPropertyImpl.kt#L321-L320
    protected fun PropertyDescriptor.isJvmFieldPropertyInCompanionObject(): Boolean {
        val container = containingClass
        if (container == null || !container.isCompanion) return false
        val outerClass = container.containingClass
        return outerClass?.isInterface != true
    }

    protected fun isNotNullProperty(): Boolean =
        !property.returnType.isNullableType()
}

internal class PropertyGetterDescriptorImpl(
    override val property: PropertyDescriptorImpl
) : PropertyAccessorDescriptorImpl(property), PropertyGetterDescriptor {
    override val name: Name
        get() = "<get-${property.name}>"

    private val getter = property.kmProperty.getter

    override val isInline: Boolean
        get() = getter.isInline
    override val isExternal: Boolean
        get() = getter.isExternal
    override val isOperator: Boolean
        get() = false
    override val isInfix: Boolean
        get() = false
    override val isSuspend: Boolean
        get() = false
    override val modality: Modality
        get() = getter.modality

    override val visibility: KVisibility?
        get() = property.kmProperty.getter.visibility.toKVisibility()


    override val signature: JvmMethodSignature?
        get() = property.kmProperty.getterSignature

    override val valueParameters: List<ValueParameterDescriptor>
        get() = emptyList()

    override val member: Method? by lazy {
        property.jvmSignature.getterSignature?.let { signature ->
            property.container.findMethodBySignature(signature.name, signature.descriptor)
        }
    }

    override fun computeFieldCaller(field: Field): Caller<*> = when {
        property.isJvmFieldPropertyInCompanionObject() || !Modifier.isStatic(field.modifiers) ->
            CallerImpl.FieldGetter.Instance(field)
        isJvmStaticProperty() ->
            CallerImpl.FieldGetter.JvmStaticInObject(field)
        else ->
            CallerImpl.FieldGetter.Static(field)
    }
}

internal class PropertySetterDescriptorImpl(
    override val property: PropertyDescriptorImpl
) : PropertyAccessorDescriptorImpl(property), PropertySetterDescriptor {

    private val setter = property.kmProperty.setter!!

    override val name: Name
        get() = "<set-${property.name}>"

    override val isInline: Boolean
        get() = setter.isInline
    override val isExternal: Boolean
        get() = setter.isExternal
    override val isOperator: Boolean
        get() = false
    override val isInfix: Boolean
        get() = false
    override val isSuspend: Boolean
        get() = false
    override val modality: Modality
        get() = setter.modality

    override val signature: JvmMethodSignature?
        get() = property.kmProperty.setterSignature

    override val visibility: KVisibility?
        get() = property.kmProperty.setter?.visibility?.toKVisibility()

    override val valueParameters: List<ValueParameterDescriptor>
        get() = listOf(PropertySetterParameterDescriptor(property.kmProperty.setterParameter, this))

    override val member: Method? by lazy {
        property.jvmSignature.setterSignature?.let { signature ->
            property.container.findMethodBySignature(signature.name, signature.descriptor)
        }
    }

    override fun computeFieldCaller(field: Field): Caller<*> = when {
        property.isJvmFieldPropertyInCompanionObject() || !Modifier.isStatic(field.modifiers) ->
            CallerImpl.FieldSetter.Instance(field, isNotNullProperty())
        isJvmStaticProperty() ->
            CallerImpl.FieldSetter.JvmStaticInObject(field, isNotNullProperty())
        else ->
            CallerImpl.FieldSetter.Static(field, isNotNullProperty())
    }
}
