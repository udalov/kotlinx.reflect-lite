/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.reflect.lite.descriptors

import kotlin.metadata.*
import kotlin.metadata.jvm.*
import kotlinx.reflect.lite.*
import kotlinx.reflect.lite.calls.Caller
import kotlinx.reflect.lite.descriptors.impl.KotlinType
import kotlinx.reflect.lite.descriptors.impl.TypeParameterTable
import kotlinx.reflect.lite.impl.KCallableImpl
import kotlinx.reflect.lite.misc.*
import kotlinx.reflect.lite.misc.JvmPropertySignature
import kotlinx.reflect.lite.misc.notImplemented
import kotlinx.reflect.lite.name.*
import java.lang.reflect.*

internal interface ModuleDescriptor {
    fun <T> findClass(name: ClassName): ClassDescriptor<*>?
}

internal interface Annotated {
    private val annotations: Annotations
        get() = notImplemented()
}

internal interface Annotations {
    fun getAll(): List<Annotation>

    object Empty : Annotations {
        override fun getAll(): List<Annotation> = emptyList()
    }

    companion object {
        val EMPTY: Empty get() = Empty
    }
}

internal interface DeclarationDescriptor : Annotated {
    val name: Name
}

internal interface ClassBasedDeclarationContainerDescriptor {
    val jClass: Class<*>
    val module: ModuleDescriptor

    val declaredMembers: Collection<KCallableImpl<*>>
    val allMembers: Collection<KCallableImpl<*>>

    fun findConstructorBySignature(desc: String): Constructor<*>?
    fun findMethodBySignature(name: String, desc: String): Method?
    fun findDefaultConstructor(desc: String): Constructor<*>?
    fun findDefaultMethod(name: String, desc: String, isMember: Boolean): Method?
}

internal interface ClassDescriptor<out T> : ClassBasedDeclarationContainerDescriptor, ClassifierDescriptor {
    val classId: ClassId

    val simpleName: String?
    val qualifiedName: String?

    val constructors: List<ConstructorDescriptor>
    val nestedClasses: List<ClassDescriptor<*>>
    val sealedSubclasses: List<ClassDescriptor<T>>

    val memberScope: MemberScope

    val containingClass: ClassDescriptor<*>?
    val thisAsReceiverParameter: ReceiverParameterDescriptor

    val typeParameterTable: TypeParameterTable
    val typeParameters: List<TypeParameterDescriptor>
    val supertypes: List<KotlinType>

    val visibility: KVisibility?

    val isInterface: Boolean
    val isObject: Boolean
    val isCompanion: Boolean

    val isFinal: Boolean
    val isOpen: Boolean
    val isAbstract: Boolean
    val isSealed: Boolean
    val isData: Boolean
    val isInner: Boolean
    val isFun: Boolean
    val isValue: Boolean
}

internal interface PackageDescriptor<out T> : ClassBasedDeclarationContainerDescriptor, ClassifierDescriptor {
    val kmPackage: KmPackage
}

internal class MemberScope(
    val properties: List<PropertyDescriptor>,
    val functions: List<FunctionDescriptor>
)

internal interface CallableDescriptor : DeclarationDescriptor {
    val module: ModuleDescriptor
    val containingClass: ClassDescriptor<*>? // todo do we need them both?
    val container: ClassBasedDeclarationContainerDescriptor

    val dispatchReceiverParameter: ReceiverParameterDescriptor?
    val extensionReceiverParameter: ReceiverParameterDescriptor?
    val valueParameters: List<ValueParameterDescriptor>

    val typeParameters: List<TypeParameterDescriptor>

    val returnType: KotlinType

    val visibility: KVisibility?

    private val isBound: Boolean
        get() = notImplemented()

    val isFinal: Boolean
    val isOpen: Boolean
    val isAbstract: Boolean
    val isReal: Boolean

    val caller: Caller<*>
    val defaultCaller: Caller<*>?
}

internal interface FunctionDescriptor : CallableDescriptor {
    val isInline: Boolean
    val isExternal: Boolean
    val isOperator: Boolean
    val isInfix: Boolean
    val isSuspend: Boolean
    val isAnnotationConstructor: Boolean

    val signature: JvmMethodSignature?

    val member: Member?
    val defaultMember: Member?
}

internal interface ParameterDescriptor : DeclarationDescriptor {
    val containingDeclaration: CallableDescriptor?
    val type: KotlinType
}

internal interface ValueParameterDescriptor : ParameterDescriptor {
    val declaresDefaultValue: Boolean
    val varargElementType: KotlinType?
}

internal interface ReceiverParameterDescriptor : ParameterDescriptor

internal interface PropertyDescriptor : CallableDescriptor {
    val isVar: Boolean
    val isLateInit: Boolean
    val isConst: Boolean
    private val isDelegated: Boolean
        get() = notImplemented()

    val isMovedFromInterfaceCompanion: Boolean

    val signature: JvmFieldSignature?
    val jvmSignature: JvmPropertySignature.KotlinProperty
    val javaField: Field?

    val getter: PropertyGetterDescriptor?
    val setter: PropertySetterDescriptor?
}

internal interface PropertyAccessorDescriptor : FunctionDescriptor {
    val property: PropertyDescriptor
}
internal interface PropertyGetterDescriptor : PropertyAccessorDescriptor
internal interface PropertySetterDescriptor : PropertyAccessorDescriptor

internal interface ConstructorDescriptor : FunctionDescriptor {
    val isPrimary: Boolean
}

internal interface ClassifierDescriptor : DeclarationDescriptor

internal interface TypeParameterDescriptor : ClassifierDescriptor {
    val containingDeclaration: DeclarationDescriptor
    val upperBounds: List<KotlinType>

    val variance: KVariance
    val isReified: Boolean
}

internal interface TypeAliasDescriptor : ClassifierDescriptor
