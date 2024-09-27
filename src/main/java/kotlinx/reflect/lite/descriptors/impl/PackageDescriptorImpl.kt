/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.reflect.lite.descriptors.impl

import kotlinx.metadata.*
import kotlinx.metadata.jvm.*
import kotlinx.metadata.jvm.KotlinClassMetadata.Companion
import kotlinx.reflect.lite.descriptors.*
import kotlinx.reflect.lite.misc.*
import kotlinx.reflect.lite.name.*

internal class PackageDescriptorImpl<T : Any?>(
    override val jClass: Class<T>
) : PackageDescriptor<T>, ClassBasedDeclarationContainerDescriptorImpl(jClass) {

    override val kmPackage: KmPackage
        get() {
            val header = jClass.getAnnotation(Metadata::class.java)?.let {
                Metadata(it.kind, it.metadataVersion, it.data1, it.data2, it.extraString, it.packageName, it.extraInt)
            } ?: error("@Metadata annotation was not found for ${jClass.name} ")
            return when (val metadata =  /* compiled code */ KotlinClassMetadata.readStrict(header)) {
                is KotlinClassMetadata.FileFacade ->  /* compiled code */ metadata.kmPackage
                is KotlinClassMetadata.MultiFileClassPart ->  /* compiled code */ metadata.kmPackage
                else -> error("Can not create PackageDescriptor for metadata of type $metadata")
            }
        }

    override val memberScope: MemberScope
        get() = MemberScope(
            kmPackage.properties.map { PropertyDescriptorImpl(it, module, null, this) },
            kmPackage.functions.map { FunctionDescriptorImpl(it, module, null, this) }
        )

    // TODO: static scope
    override val staticScope: MemberScope
        get() = MemberScope(emptyList(), emptyList())

    override val name: Name
        get() = kmPackage.moduleName ?: error("kmPackage ${kmPackage} does not have a name")
}
