package kotlinx.reflect.lite.descriptors.impl

import kotlinx.metadata.*
import kotlinx.metadata.internal.common.*
import kotlinx.metadata.jvm.*
import kotlinx.reflect.lite.builtins.*
import kotlinx.reflect.lite.descriptors.ClassDescriptor
import kotlinx.reflect.lite.descriptors.ModuleDescriptor
import kotlinx.reflect.lite.misc.*
import kotlinx.reflect.lite.name.*

internal class ModuleDescriptorImpl(internal val classLoader: ClassLoader) : ModuleDescriptor {
    override fun <T> findClass (name: ClassName): ClassDescriptor<T> {
        val fqName = (JavaToKotlinClassMap.mapKotlinToJava(FqName(name.replace('/', '.'))) ?: ClassId(name)).asJavaLookupFqName()
        val jClass = when (fqName) {
            "kotlin.BooleanArray" -> BooleanArray::class.java
            "kotlin.ByteArray" -> ByteArray::class.java
            "kotlin.CharArray" -> CharArray::class.java
            "kotlin.DoubleArray" -> DoubleArray::class.java
            "kotlin.FloatArray" -> FloatArray::class.java
            "kotlin.IntArray" -> IntArray::class.java
            "kotlin.LongArray" -> LongArray::class.java
            "kotlin.ShortArray" -> ShortArray::class.java
            "kotlin.Array" -> Array<Any>::class.java
            else -> classLoader.tryLoadClass(fqName) ?: error("Failed to load the class: $fqName")
        }
        return ClassDescriptorImpl(jClass as Class<T>)
    }
}
