/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.reflect.lite.impl

import kotlin.metadata.jvm.*
import kotlinx.reflect.lite.descriptors.*
import kotlinx.reflect.lite.KVisibility
import kotlinx.reflect.lite.descriptors.impl.*
import kotlinx.reflect.lite.descriptors.impl.KotlinType
import kotlinx.reflect.lite.calls.*
import kotlinx.reflect.lite.misc.JvmPropertySignature
import java.lang.reflect.*

internal fun createKCallable(descriptor: CallableDescriptor): KCallableImpl<*> {
    if (descriptor is PropertyDescriptor) {
        val receiverCount = (descriptor.dispatchReceiverParameter?.let { 1 } ?: 0) +
                (descriptor.extensionReceiverParameter?.let { 1 } ?: 0)

        when {
            descriptor.isVar ->
                return when (receiverCount) {
                    0 -> KMutableProperty0Impl<Any?>(descriptor)
                    1 -> KMutableProperty1Impl<Any?, Any?>(descriptor)
                    2 -> KMutableProperty2Impl<Any?, Any?, Any?>(descriptor)
                    else -> error("Property with $receiverCount receivers is not possible")
                }
            else -> return when (receiverCount) {
                0 -> KProperty0Impl<Any?>(descriptor)
                1 -> KProperty1Impl<Any?, Any?>(descriptor)
                2 -> KProperty2Impl<Any?, Any?, Any?>(descriptor)
                else -> error("Property with $receiverCount receivers is not possible")
            }
        }
    }
    if (descriptor is FunctionDescriptor) {
        return KFunctionImpl(descriptor)
    }
    throw KotlinReflectionInternalError("Unsupported callable: $descriptor")
}

internal val CallableDescriptor.instanceReceiverParameter: ReceiverParameterDescriptor?
    get() = if (dispatchReceiverParameter != null) containingClass?.thisAsReceiverParameter else null

internal fun <D : CallableDescriptor> overrides(derived: D, base: D): Boolean {
    require(derived.name == base.name) { "Names should be equal: $derived, $base" }
    if (derived is PropertyDescriptor && base is PropertyDescriptor) {
        // TODO (!)
        return true
    } else if (derived is FunctionDescriptor && base is FunctionDescriptor) {
        // TODO (!)
        return true
    } else {
        error("Unknown members: $derived, $base")
    }
}

internal fun <D : CallableDescriptor> addFakeOverrides(
    klass: ClassDescriptor<*>,
    realMembers: List<D>,
    selector: (ClassDescriptor<*>) -> List<D>,
    createFakeOverride: (List<D>) -> D
): List<D> {
    val fromDerived = realMembers.groupBy(CallableDescriptor::name)

    val fromBase = mutableMapOf<String, MutableList<D>>()
    for (supertype in klass.supertypes) {
        val superclass = supertype.descriptor as? ClassDescriptor<*> ?: continue
        val allMembers = selector(superclass).filter { it.visibility != KVisibility.PRIVATE }
        for ((name, members) in allMembers.groupBy(CallableDescriptor::name)) {
            fromBase.getOrPut(name) { ArrayList(1) }.addAll(members)
        }
    }

    val result = mutableListOf<D>()
    for ((name, members) in fromBase) {
        val notOverridden = members.filterNot { baseMember ->
            fromDerived[name].orEmpty().any { derivedMember -> overrides(derivedMember, baseMember)}
        }
        if (notOverridden.isEmpty()) continue
        // TODO (!): if > 1, group by extension receiver and parameter types
        // TODO: filterOutOverridden (so that `isAbstract = notOverridden.all { it.isAbstract }` would work)
        result.add(createFakeOverride(notOverridden))
    }

    return result
}

internal fun addPropertyFakeOverrides(klass: ClassDescriptor<*>, realProperties: List<PropertyDescriptor>): List<PropertyDescriptor> =
    addFakeOverrides(klass, realProperties, { it.memberScope.properties }) { members ->
        val representative = members.first()
        // TODO (!): type substitution
        FakeOverridePropertyDescriptor(
            klass.module,
            klass,
            null, // todo: representative.extensionReceiverParameter
            representative.valueParameters,
            representative.typeParameters,
            representative.returnType,
            members
        )
    }

internal fun addFunctionFakeOverrides(klass: ClassDescriptor<*>, realFunctions: List<FunctionDescriptor>): List<FunctionDescriptor> =
    addFakeOverrides(klass, realFunctions, { it.memberScope.functions }) { members ->
        // TODO (!): type substitution
        val representative = members.first()
        FakeOverrideFunctionDescriptor(
            klass.module,
            klass,
            null, // todo: representative.extensionReceiverParameter
            representative.valueParameters,
            representative.typeParameters,
            representative.returnType,
            members
        )
    }

internal abstract class FakeOverrideCallableMemberDescriptor(
    override val module: ModuleDescriptor,
    override val containingClass: ClassDescriptor<*>,
    override val extensionReceiverParameter: ReceiverParameterDescriptor?,
    override val valueParameters: List<ValueParameterDescriptor>,
    override val typeParameters: List<TypeParameterDescriptor>,
    override val returnType: KotlinType,
    val overridden: List<CallableDescriptor>
) : CallableDescriptor {
    init {
        require(overridden.isNotEmpty())
    }

    override val name: String
        get() = overridden.first().name
    override val visibility: KVisibility?
        get() = overridden.first().visibility

    override val container: ClassBasedDeclarationContainerDescriptor
        get() = containingClass

    override val dispatchReceiverParameter: ReceiverParameterDescriptor?
        get() = containingClass.thisAsReceiverParameter

    override val isFinal: Boolean
        get() = overridden.any(CallableDescriptor::isFinal)
    override val isAbstract: Boolean
        get() = overridden.all(CallableDescriptor::isAbstract)
    override val isOpen: Boolean
        get() = !isFinal && !isAbstract

    override val isReal: Boolean
        get() = false
}

internal class FakeOverrideFunctionDescriptor(
    module: ModuleDescriptor,
    containingClass: ClassDescriptor<*>,
    extensionReceiverParameter: ReceiverParameterDescriptor?,
    valueParameters: List<ValueParameterDescriptor>,
    typeParameters: List<TypeParameterDescriptor>,
    returnType: KotlinType,
    overridden: List<FunctionDescriptor>
) : FakeOverrideCallableMemberDescriptor(
    module, containingClass, extensionReceiverParameter, valueParameters, typeParameters, returnType, overridden
), FunctionDescriptor {
    @Suppress("UNCHECKED_CAST")
    private val overriddenFunctions: List<FunctionDescriptor>
        get() = super.overridden as List<FunctionDescriptor>

    override val signature: JvmMethodSignature?
        get() = overriddenFunctions.first().signature

    override val isInline: Boolean
        get() = overriddenFunctions.first().isInline
    override val isOperator: Boolean
        get() = overriddenFunctions.first().isOperator
    override val isInfix: Boolean
        get() = overriddenFunctions.first().isInfix
    override val isSuspend: Boolean
        get() = overriddenFunctions.first().isSuspend
    override val isExternal: Boolean
        get() = overriddenFunctions.first().isExternal

    override val isAnnotationConstructor: Boolean
        get() = overriddenFunctions.first().isAnnotationConstructor

    override val member: Member? by lazy {
        overriddenFunctions.first().member
    }

    override val defaultMember: Member? by lazy {
        overriddenFunctions.first().defaultMember
    }

    override val caller: Caller<*> by lazy {
        overriddenFunctions.first().caller
    }

    override val defaultCaller: Caller<*>? by lazy {
        overriddenFunctions.first().defaultCaller
    }
}

// TODO: fix inheritance hierarchy for overridden properties

internal class FakeOverridePropertyDescriptor(
    module: ModuleDescriptor,
    containingClass: ClassDescriptor<*>,
    extensionReceiverParameter: ReceiverParameterDescriptor?,
    valueParameters: List<ValueParameterDescriptor>,
    typeParameters: List<TypeParameterDescriptor>,
    returnType: KotlinType,
    overridden: List<PropertyDescriptor>
) : FakeOverrideCallableMemberDescriptor(
    module, containingClass, extensionReceiverParameter, valueParameters, typeParameters, returnType, overridden
),  PropertyDescriptor {
    @Suppress("UNCHECKED_CAST")
    val overriddenProperties: List<PropertyDescriptor>
        get() = super.overridden as List<PropertyDescriptor>

    override val isVar: Boolean
        get() = overriddenProperties.any(PropertyDescriptor::isVar)
    override val isLateInit: Boolean
        get() = false
    override val isConst: Boolean
        get() = false
    override val isMovedFromInterfaceCompanion: Boolean
        get() = overriddenProperties.first().isMovedFromInterfaceCompanion

    override val signature: JvmFieldSignature?
        get() = overriddenProperties.first().signature

    override val jvmSignature: JvmPropertySignature.KotlinProperty by lazy {
        // TODO: inherit this from PropertyDescriptorImpl
        val property = overriddenProperties.first() as PropertyDescriptorImpl
        JvmPropertySignature.KotlinProperty(
            property,
            property.kmProperty.fieldSignature,
            property.kmProperty.getterSignature,
            property.kmProperty.setterSignature
        )
    }

    override val javaField: Field? by lazy {
        // TODO: copied from PropertyDescriptorImpl, inherit the implementation
        jvmSignature.fieldSignature?.let {
            // TODO: support propertyWithBackingFieldInOuterClass
            // TODO: support JavaField, JavaMethodProperty, MappedKotlinProperty
            val owner = containingClass?.jClass ?: container.jClass
            try {
                owner.getDeclaredField(it.name)
            } catch (e: NoSuchFieldException) {
                null
            }
        }
    }

    override val getter: PropertyGetterDescriptor?
        get() = FakeOverridePropertyGetterDescriptor(this)

    override val setter: PropertySetterDescriptor?
        get() =  if (isVar) FakeOverridePropertySetterDescriptor(this) else null

    override val caller: Caller<*> by lazy {
        getter?.caller ?: error("The property has no getter")
    }

    override val defaultCaller: Caller<*> by lazy {
        getter?.defaultCaller ?: error("The property has no getter")
    }
}

internal class FakeOverridePropertyGetterDescriptor(
    override val property: FakeOverridePropertyDescriptor
) : FakeOverrideCallableMemberDescriptor(
    property.module,
    property.containingClass,
    property.extensionReceiverParameter,
    property.valueParameters,
    property.typeParameters,
    property.returnType,
    property.overriddenProperties.mapNotNull(PropertyDescriptor::getter)
), PropertyGetterDescriptor {
    @Suppress("UNCHECKED_CAST")
    private val overriddenPropertyGetters: List<PropertyGetterDescriptor>
        get() = super.overridden as List<PropertyGetterDescriptor>

    override val signature: JvmMethodSignature?
        get() = overriddenPropertyGetters.first().signature

    override val isInline: Boolean
        get() = overriddenPropertyGetters.first().isInline
    override val isOperator: Boolean get() = false
    override val isInfix: Boolean get() = false
    override val isSuspend: Boolean get() = false
    override val isExternal: Boolean
        get() = overriddenPropertyGetters.first().isExternal

    override val isAnnotationConstructor: Boolean
        get() = overriddenPropertyGetters.first().isAnnotationConstructor

    override val member: Member? by lazy {
        overriddenPropertyGetters.first().member
    }

    override val defaultMember: Member? by lazy {
        overriddenPropertyGetters.first().defaultMember
    }

    override val caller: Caller<*> by lazy {
        overriddenPropertyGetters.first().caller
    }

    override val defaultCaller: Caller<*>? by lazy {
        overriddenPropertyGetters.first().defaultCaller
    }
}

internal class FakeOverridePropertySetterDescriptor(
    override val property: FakeOverridePropertyDescriptor
) : FakeOverrideCallableMemberDescriptor(
    property.module,
    property.containingClass,
    property.extensionReceiverParameter,
    property.valueParameters,
    property.typeParameters,
    property.returnType,
    property.overriddenProperties.mapNotNull(PropertyDescriptor::setter)
), PropertySetterDescriptor {
    @Suppress("UNCHECKED_CAST")
    private val overriddenPropertySetters: List<PropertySetterDescriptor>
        get() = super.overridden as List<PropertySetterDescriptor>

    override val signature: JvmMethodSignature?
        get() = overriddenPropertySetters.first().signature

    override val isInline: Boolean
        get() = overriddenPropertySetters.first().isInline // TODO?
    override val isOperator: Boolean get() = false
    override val isInfix: Boolean get() = false
    override val isSuspend: Boolean get() = false

    override val isExternal: Boolean
        get() = overriddenPropertySetters.first().isExternal

    override val isAnnotationConstructor: Boolean
        get() = overriddenPropertySetters.first().isAnnotationConstructor

    override val member: Member? by lazy {
        overriddenPropertySetters.first().member
    }

    override val defaultMember: Member? by lazy {
        overriddenPropertySetters.first().defaultMember
    }

    override val caller: Caller<*> by lazy {
        overriddenPropertySetters.first().caller
    }

    override val defaultCaller: Caller<*>? by lazy {
        overriddenPropertySetters.first().defaultCaller
    }
}
