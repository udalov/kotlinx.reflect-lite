/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.reflect.lite.impl

import kotlin.metadata.jvm.*
import kotlinx.reflect.lite.*
import kotlinx.reflect.lite.descriptors.ClassDescriptor
import kotlinx.reflect.lite.descriptors.PackageDescriptor
import kotlinx.reflect.lite.descriptors.impl.*
import kotlinx.reflect.lite.jvm.internal.*
import kotlinx.reflect.lite.jvm.internal.createCache
import kotlinx.reflect.lite.misc.*

internal object ReflectionLiteImpl {

    private val kClassCache = createCache { createKotlinClass(it) }

    fun <T : Any> createKotlinDeclarationContainer(jClass: Class<T>): KDeclarationContainer {
        return when (jClass.getMetadataAnnotation()?.kind) {
            null, KotlinClassMetadata.CLASS_KIND -> getOrCreateKotlinClass(jClass)
            KotlinClassMetadata.FILE_FACADE_KIND, KotlinClassMetadata.MULTI_FILE_CLASS_PART_KIND -> createKotlinPackage(jClass)
            else -> throw KotlinReflectionInternalError("Can not load class metadata for $jClass")
        }
    }

    @Suppress("UNCHECKED_CAST")
    public fun <T: Any> getOrCreateKotlinClass(jClass: Class<T>): KClass<T> = kClassCache.get(jClass) as KClass<T>

    private fun <T: Any> createKotlinClass(jClass: Class<T>): KClass<T> {
        val kind = jClass.getMetadataAnnotation()?.kind
        return KClassImpl(createClassDescriptor(jClass, kind))
    }

    fun <T: Any> createKotlinPackage(jClass: Class<T>): KPackage<T> {
        val kind = jClass.getMetadataAnnotation()?.kind
        return KPackageImpl(createPackageDescriptor(jClass, kind))
    }

    private fun <T: Any> createClassDescriptor(jClass: Class<T>, kind: Int?): ClassDescriptor<T> =
        when (kind) {
            null -> {
                if (RuntimeTypeMapper.getKotlinBuiltInClassId(jClass) != null) {
                    ClassDescriptorImpl(jClass)
                } else {
                    JavaClassDescriptor(jClass)
                }
            }
            KotlinClassMetadata.CLASS_KIND -> {
                ClassDescriptorImpl(jClass)
            }
            else -> throw KotlinReflectionInternalError("Could not create an instance of KClass from $this, the class file kind equals $kind")
        }

    private fun <T: Any> createPackageDescriptor(jClass: Class<T>, kind: Int?): PackageDescriptor<T> =
        when (kind) {
            // we can extract KmPackage only from these kinds of kotlin class files: FILE_FACADE_KIND, MULTI_FILE_CLASS_PART_KIND
            KotlinClassMetadata.FILE_FACADE_KIND, KotlinClassMetadata.MULTI_FILE_CLASS_PART_KIND -> PackageDescriptorImpl(jClass)
            else -> throw KotlinReflectionInternalError("Could not create an instance of KPackage from $jClass, the class file kind equals $kind")
        }

    private fun <T: Any> Class<T>.getMetadataAnnotation() = getAnnotation(Metadata::class.java)
}
