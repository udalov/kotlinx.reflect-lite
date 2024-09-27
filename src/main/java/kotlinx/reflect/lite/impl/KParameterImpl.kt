/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.reflect.lite.impl

import kotlinx.reflect.lite.*
import kotlinx.reflect.lite.descriptors.*

internal class KParameterImpl(
    val descriptor: ParameterDescriptor,
    override val index: Int,
    override val kind: KParameter.Kind,
    private val containingCallable: CallableDescriptor
): KParameter {

    override val name: String?
        get() {
            val valueParameter = descriptor as? ValueParameterDescriptor ?: return null
            val name = valueParameter.name
            // for special names (like the name of property setterParameter "<set-?>") return null
            return if (name.startsWith("<")) null else name
        }

    // Logic from here: https://github.com/JetBrains/kotlin/blob/1f1790d60e837347d99921dd1fb4f00e6ec868d2/core/reflection.jvm/src/kotlin/reflect/jvm/internal/KParameterImpl.kt#L42
    override val type: KType by lazy {
        KTypeImpl(descriptor.type) {
            val descriptor = descriptor

            if (descriptor is ReceiverParameterDescriptor &&
                containingCallable.instanceReceiverParameter == descriptor &&
                !containingCallable.isReal
            ) {
                // In case of fake overrides, dispatch receiver type should be computed manually because Caller.parameterTypes returns
                // types from Java reflection where receiver is always the declaring class of the original declaration
                // (not the class where the fake override is generated, which is returned by KParameter.type)
                containingCallable.containingClass?.jClass
                    ?: throw KotlinReflectionInternalError("Cannot determine receiver Java type of inherited declaration: $descriptor")
            } else {
                containingCallable.caller.parameterTypes[index]
            }
        }
    }

    override val isOptional: Boolean
        get() = (descriptor as? ValueParameterDescriptor)?.declaresDefaultValue ?: false

    override val isVararg: Boolean
        get() = (descriptor is ValueParameterDescriptor) && descriptor.varargElementType != null

    override fun equals(other: Any?) =
        other is KParameterImpl &&
        descriptor.containingDeclaration == other.descriptor.containingDeclaration &&
        index == other.index

    override fun hashCode() =
        (descriptor.containingDeclaration.hashCode() * 31) + index.hashCode()
}
