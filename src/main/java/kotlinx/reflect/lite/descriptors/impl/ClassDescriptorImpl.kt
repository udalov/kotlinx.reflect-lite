package kotlinx.reflect.lite.descriptors.impl

import kotlinx.metadata.*
import kotlinx.metadata.internal.common.*
import kotlinx.metadata.jvm.*
import kotlinx.reflect.lite.*
import kotlinx.reflect.lite.builtins.*
import kotlinx.reflect.lite.descriptors.*
import kotlinx.reflect.lite.descriptors.ClassDescriptor
import kotlinx.reflect.lite.descriptors.ConstructorDescriptor
import kotlinx.reflect.lite.descriptors.FunctionDescriptor
import kotlinx.reflect.lite.descriptors.MemberScope
import kotlinx.reflect.lite.descriptors.ModuleDescriptor
import kotlinx.reflect.lite.descriptors.PropertyDescriptor
import kotlinx.reflect.lite.descriptors.TypeParameterDescriptor
import kotlinx.reflect.lite.impl.KClassImpl
import kotlinx.reflect.lite.misc.*
import kotlinx.reflect.lite.name.*

internal class ClassDescriptorImpl<T : Any?> internal constructor(
    override val jClass: Class<T>
) : ClassDescriptor<T> {

    override val kmClass: KmClass = jClass.getKmClass()

    override val module = ModuleDescriptorImpl(jClass.safeClassLoader)

    override val classId: ClassId = jClass.classId

    private fun Class<*>.getKmClass(): KmClass {
        val builtinClassId = JavaToKotlinClassMap.mapJavaToKotlin(FqName(name))
        return if (builtinClassId != null) {
            val packageName = builtinClassId.packageFqName
            // kotlin.collections -> kotlin/collections/collections.kotlin_builtins
            val resourcePath = packageName.asString().replace('.', '/') + '/' + packageName.shortName() + ".kotlin_builtins"
            val bytes = Unit::class.java.classLoader.getResourceAsStream(resourcePath)?.readBytes()
                ?: error("No builtins metadata file found: $resourcePath") // TODO: return null
            val packageFragment = KotlinCommonMetadata.read(bytes)?.toKmModuleFragment()
                ?: error("Incompatible metadata version: $resourcePath") // TODO
            packageFragment.classes.find { it.name == builtinClassId.asClassName() }
                ?: error("Built-in class not found: $builtinClassId in $resourcePath")
        } else {
            val header = getAnnotation(Metadata::class.java)?.let {
                KotlinClassHeader(it.kind, it.metadataVersion, it.data1, it.data2, it.extraString, it.packageName, it.extraInt)
            } ?: error("@Metadata annotation was not found for ${name} ")
            val metadata = KotlinClassMetadata.read(header)
            (metadata as? KotlinClassMetadata.Class)?.toKmClass()
                ?: error("KotlinClassMetadata.Class metadata is only supported for now")
        }
    }

    override val name: Name
        get() = kmClass.name.substringAfterLast('.').substringAfterLast('/')

    override val simpleName: String?
        get() {
            if (jClass.isAnonymousClass) return null

            val classId = jClass.classId
            return when {
                classId.isLocal -> calculateLocalClassName(jClass)
                else -> classId.shortClassName
            }
        }

    override val qualifiedName: String?
        get() {
            if (jClass.isAnonymousClass) return null

            val classId = jClass.classId
            return when {
                classId.isLocal -> null
                else -> classId.asSingleFqName().asString()
            }
        }

    private fun calculateLocalClassName(jClass: Class<*>): String {
        val name = jClass.simpleName
        jClass.enclosingMethod?.let { method ->
            return name.substringAfter(method.name + "$")
        }
        jClass.enclosingConstructor?.let { constructor ->
            return name.substringAfter(constructor.name + "$")
        }
        return name.substringAfter('$')
    }

    override val constructors: List<ConstructorDescriptor>
        get() = kmClass.constructors.map { ConstructorDescriptorImpl(it, module, this) }

    override val nestedClasses: List<ClassDescriptor<*>>
        get() = kmClass.nestedClasses.map { module.findClass<Any?>(classId.createNestedClassId(it).asClassName()) }

    override val sealedSubclasses: List<ClassDescriptor<T>>
        get() = kmClass.sealedSubclasses.map { module.findClass(it) }

    override val properties: List<PropertyDescriptor>
        get() = kmClass.properties.map { PropertyDescriptorImpl(it, module, this) }

    override val functions: List<FunctionDescriptor>
        get() = kmClass.functions.map { FunctionDescriptorImpl(it, module, this) }

    // TODO: static members
    // TODO: function and property fake overrides
    override val memberScope: MemberScope
        get() = MemberScope(
            kmClass.properties.map { PropertyDescriptorImpl(it, module, this) },
            kmClass.functions.map { FunctionDescriptorImpl(it, module, this) }
        )

    override val visibility: KVisibility?
        get() = kmClass.flags.toVisibility()

    override val typeParameterTable: TypeParameterTable =
        kmClass.typeParameters.toTypeParameters(this, module, containingClass?.typeParameterTable)

    internal val containingClass: ClassDescriptor<*>?
        get() = classId.getOuterClassId()?.let { module.findClass<Any?>(it.asClassName()) }

    override val typeParameters: List<TypeParameterDescriptor>
        get() = typeParameterTable.typeParameters

    override val supertypes: List<KotlinType>
        get() = kmClass.supertypes.map { it.toKotlinType(module, typeParameterTable) }

    override val isInterface: Boolean
        get() = Flag.Class.IS_INTERFACE(kmClass.flags)
    override val isObject: Boolean
        get() = Flag.Class.IS_OBJECT(kmClass.flags)
    override val isCompanionObject: Boolean
        get() = Flag.Class.IS_COMPANION_OBJECT(kmClass.flags)
    override val isFinal: Boolean
        get() = Flag.Common.IS_FINAL(kmClass.flags)
    override val isOpen: Boolean
        get() = Flag.Common.IS_OPEN(kmClass.flags)
    override val isAbstract: Boolean
        get() = Flag.Common.IS_ABSTRACT(kmClass.flags)
    override val isSealed: Boolean
        get() = Flag.Common.IS_SEALED(kmClass.flags)
    override val isData: Boolean
        get() = Flag.Class.IS_DATA(kmClass.flags)
    override val isInner: Boolean
        get() = Flag.Class.IS_INNER(kmClass.flags)
    override val isCompanion: Boolean
        get() = Flag.Class.IS_COMPANION_OBJECT(kmClass.flags)
    override val isFun: Boolean
        get() = Flag.Class.IS_FUN(kmClass.flags)
    override val isValue: Boolean
        get() = Flag.Class.IS_VALUE(kmClass.flags)
}