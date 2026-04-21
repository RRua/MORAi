package dev.memoize.plugin

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

/**
 * Factory that creates MemoizeClassVisitor instances for classes that contain
 * @Memoize or @CacheInvalidate annotated methods.
 */
abstract class MemoizeClassVisitorFactory :
    AsmClassVisitorFactory<InstrumentationParameters.None> {

    companion object {
        const val MEMOIZE_DESCRIPTOR = "Ldev/memoize/annotations/Memoize;"
        const val CACHE_INVALIDATE_DESCRIPTOR = "Ldev/memoize/annotations/CacheInvalidate;"
    }

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        return MemoizeClassVisitor(Opcodes.ASM9, nextClassVisitor, classContext.currentClassData.className)
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        // Instrument any class that has our annotations in its method annotations.
        // The AGP ClassData provides class-level annotations only, so we need to
        // instrument all project classes and check method annotations inside the visitor.
        // For efficiency, we skip known framework/library packages.
        val className = classData.className
        return !className.startsWith("android.") &&
               !className.startsWith("androidx.") &&
               !className.startsWith("kotlin.") &&
               !className.startsWith("java.") &&
               !className.startsWith("dev.memoize.runtime.") &&
               !className.startsWith("dev.memoize.annotations.")
    }
}
