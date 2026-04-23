package io.github.sanadlab.plugin

import org.objectweb.asm.*
import org.objectweb.asm.commons.AdviceAdapter
import org.objectweb.asm.tree.*

/**
 * ASM ClassVisitor that uses the tree API to first collect all annotation info,
 * then instruments the class in a single writeout pass.
 *
 * Strategy:
 * 1. Read entire class into a ClassNode (tree API)
 * 2. Scan all methods for @Memoize and @CacheInvalidate annotations
 * 3. If annotations found: add fields, instrument methods, patch constructors
 * 4. Write the modified ClassNode to the next ClassVisitor
 *
 * Method overloading is supported via a 5-digit hex hash suffix derived from the
 * method name and descriptor. For example, two overloads of search():
 *   search(int)    -> __memoDispatcher_search_a3f2b
 *   search(String) -> __memoDispatcher_search_c7d1e
 *
 * @CacheInvalidate("search") resolves to ALL overloads at build time.
 */
class MemoizeClassVisitor private constructor(
    api: Int,
    private val nextVisitor: ClassVisitor,
    private val className: String,
    // We collect into a ClassNode and piggyback on ClassVisitor's built-in
    // delegation so EVERY visit* call (including visitNestHost / visitNestMember
    // / visitPermittedSubclass / visitRecordComponent / visitModule /
    // visitTypeAnnotation) is forwarded automatically. Manually forwarding a
    // subset would silently drop the rest — this is exactly what caused
    // IllegalAccessError for nestmates under Java 11+ nest-based access control.
    private val classNode: ClassNode
) : ClassVisitor(api, classNode) {

    constructor(api: Int, nextVisitor: ClassVisitor, className: String)
            : this(api, nextVisitor, className, ClassNode(api))

    companion object {
        const val MEMOIZE_DESC = "Lio/github/sanadlab/annotations/Memoize;"
        const val CACHE_INVALIDATE_DESC = "Lio/github/sanadlab/annotations/CacheInvalidate;"
        const val INVALIDATE_ENTRY_DESC = "Lio/github/sanadlab/annotations/InvalidateCacheEntry;"
        const val MANAGER_FIELD = "__memoCacheManager"
        const val MANAGER_INTERNAL = "io/github/sanadlab/runtime/MemoCacheManager"
        const val MANAGER_DESC = "Lio/github/sanadlab/runtime/MemoCacheManager;"
        const val DISPATCHER_INTERNAL = "io/github/sanadlab/runtime/MemoDispatcher"
        const val DISPATCHER_DESC = "Lio/github/sanadlab/runtime/MemoDispatcher;"
        const val CACHE_KEY_INTERNAL = "io/github/sanadlab/runtime/CacheKeyWrapper"
        const val CACHE_KEY_DESC = "Lio/github/sanadlab/runtime/CacheKeyWrapper;"
        const val OBJECT_ARRAY_DESC = "[Ljava/lang/Object;"

        /**
         * Generates a unique key for a method based on its name and descriptor.
         * The key is: name_XXXXX where XXXXX is a 5-hex-char hash of (name + descriptor).
         *
         * This ensures overloaded methods (same name, different parameter types)
         * get unique field names and manager registration keys.
         *
         * Examples:
         *   search(I)Z         -> search_0a3f2
         *   search(Ljava/lang/String;)Z -> search_c71de
         *   length()I          -> length_b8a01
         */
        fun methodKey(name: String, descriptor: String): String {
            val hash = (name + descriptor).hashCode() and 0xFFFFF // 20 bits = 5 hex digits
            return "${name}_${"%05x".format(hash)}"
        }
    }

    data class MemoMethodInfo(
        val name: String,
        val descriptor: String,
        val key: String,             // unique key = name_XXXXX
        val maxSize: Int = 128,
        val expireAfterWrite: Long = -1,
        val eviction: String = "LRU",
        val threadSafety: String = "CONCURRENT",
        val recordStats: Boolean = false,
        val autoMonitor: Boolean = false,
        val minHitRate: Double = 0.3,
        val monitorWindow: Int = 100
    )

    enum class InvalidationMode { FLUSH, KEYS, KEY_BUILDER }

    /**
     * Resolved per-target directive from a nested @Invalidation. [targetMethodKey]
     * is already the unique methodKey of the @Memoize target method.
     *
     * FLUSH mode        => emit manager.invalidate(new String[]{targetMethodKey})
     * KEYS mode         => emit manager.invalidateEntry(targetMethodKey, Object[]{boxed enclosing params...})
     * KEY_BUILDER mode  => emit invocation of the enclosing class's helper method,
     *                      then forward its return value (wrapped if non-array) as the args tuple.
     */
    data class StructuredInvalidation(
        val targetMethodKey: String,
        val mode: InvalidationMode,
        val keyIndices: IntArray = IntArray(0),
        val keyBuilderName: String = "",
        val keyBuilderDesc: String = "",
        val keyBuilderIsStatic: Boolean = false,
        val keyBuilderIsPrivate: Boolean = false,
        val keyBuilderReturnsObjectArray: Boolean = false,
        val keyBuilderArgs: IntArray = IntArray(0)
    )

    data class InvalidateMethodInfo(
        val name: String,
        val descriptor: String,
        // Legacy @CacheInvalidate("a", "b") names resolved to unique methodKeys.
        // Empty array means "invalidate all caches" when structured is also empty.
        val legacyTargets: List<String>,
        val structured: List<StructuredInvalidation> = emptyList()
    )

    /**
     * Collected state for a method annotated with @InvalidateCacheEntry.
     * [targetMethodKey] is already resolved to the unique methodKey (name_XXXXX)
     * of the @Memoize method whose cache we'll evict a row from. [keyIndices]
     * are the enclosing method's parameter indices used to rebuild the target
     * cache key, in target-signature order.
     */
    data class InvalidateEntryMethodInfo(
        val name: String,
        val descriptor: String,
        val targetMethodKey: String,
        val keyIndices: IntArray
    )

    // Raw scan result for @CacheInvalidate; resolved after the @Memoize scan.
    private data class RawInvalidate(
        val name: String,
        val descriptor: String,
        val valueTargets: List<String>,
        val rawStructured: List<AnnotationNode>
    )

    override fun visitEnd() {
        // super.visitEnd() forwards to classNode.visitEnd() via the ClassVisitor
        // delegation chain. We deliberately do NOT also forward to nextVisitor
        // here — we take ownership from this point and decide below whether to
        // emit the class unchanged or run the instrumentation pipeline.
        super.visitEnd()

        // Phase 1: Scan for annotated methods
        val memoizedMethods = mutableListOf<MemoMethodInfo>()
        val rawInvalidates = mutableListOf<RawInvalidate>()
        // Raw scan results for @InvalidateCacheEntry; resolved to methodKeys after the memoize scan completes.
        val rawInvalidateEntries = mutableListOf<Triple<MethodNode, String, IntArray>>()

        for (method in classNode.methods) {
            if (method.visibleAnnotations == null && method.invisibleAnnotations == null) continue

            val allAnnotations = (method.visibleAnnotations.orEmpty()) + (method.invisibleAnnotations.orEmpty())

            for (ann in allAnnotations) {
                when (ann.desc) {
                    INVALIDATE_ENTRY_DESC -> {
                        var targetMethod = ""
                        var keyIndices = IntArray(0)
                        if (ann.values != null) {
                            val values = ann.values
                            var i = 0
                            while (i < values.size - 1) {
                                when (values[i] as? String) {
                                    "method" -> targetMethod = values[i + 1] as? String ?: ""
                                    "keys" -> {
                                        val raw = values[i + 1]
                                        if (raw is List<*>) {
                                            keyIndices = raw.filterIsInstance<Int>().toIntArray()
                                        }
                                    }
                                }
                                i += 2
                            }
                        }
                        if (targetMethod.isNotEmpty()) {
                            rawInvalidateEntries.add(Triple(method, targetMethod, keyIndices))
                        }
                    }
                    MEMOIZE_DESC -> {
                        var maxSize = 128
                        var expireAfterWrite = -1L
                        var eviction = "LRU"
                        var threadSafety = "CONCURRENT"
                        var recordStats = false
                        var autoMonitor = false
                        var minHitRate = 0.3
                        var monitorWindow = 100
                        if (ann.values != null) {
                            val values = ann.values
                            var i = 0
                            while (i < values.size - 1) {
                                when (values[i] as? String) {
                                    "maxSize" -> maxSize = values[i + 1] as? Int ?: 128
                                    "expireAfterWrite" -> expireAfterWrite = values[i + 1] as? Long ?: -1L
                                    "recordStats" -> recordStats = values[i + 1] as? Boolean ?: false
                                    "autoMonitor" -> autoMonitor = values[i + 1] as? Boolean ?: false
                                    "minHitRate" -> minHitRate = values[i + 1] as? Double ?: 0.3
                                    "monitorWindow" -> monitorWindow = values[i + 1] as? Int ?: 100
                                    "eviction" -> {
                                        val enumVal = values[i + 1] as? Array<*>
                                        if (enumVal != null && enumVal.size == 2) {
                                            eviction = enumVal[1] as? String ?: "LRU"
                                        }
                                    }
                                    "threadSafety" -> {
                                        val enumVal = values[i + 1] as? Array<*>
                                        if (enumVal != null && enumVal.size == 2) {
                                            threadSafety = enumVal[1] as? String ?: "CONCURRENT"
                                        }
                                    }
                                }
                                i += 2
                            }
                        }
                        val key = methodKey(method.name, method.desc)
                        memoizedMethods.add(MemoMethodInfo(
                            method.name, method.desc, key, maxSize,
                            expireAfterWrite, eviction, threadSafety, recordStats,
                            autoMonitor, minHitRate, monitorWindow
                        ))
                    }
                    CACHE_INVALIDATE_DESC -> {
                        val valueTargets = mutableListOf<String>()
                        val rawStructured = mutableListOf<AnnotationNode>()
                        if (ann.values != null) {
                            val values = ann.values
                            var i = 0
                            while (i < values.size - 1) {
                                when (values[i] as? String) {
                                    "value" -> {
                                        val v = values[i + 1]
                                        if (v is List<*>) {
                                            valueTargets.addAll(v.filterIsInstance<String>())
                                        }
                                    }
                                    "targets" -> {
                                        val v = values[i + 1]
                                        if (v is List<*>) {
                                            rawStructured.addAll(v.filterIsInstance<AnnotationNode>())
                                        }
                                    }
                                }
                                i += 2
                            }
                        }
                        rawInvalidates.add(RawInvalidate(method.name, method.desc, valueTargets, rawStructured))
                    }
                }
            }
        }

        // Resolve @InvalidateCacheEntry raw scans into methodKey-qualified infos.
        // Pick the first @Memoize method whose simple name matches the target.
        // Overload disambiguation is deliberately out of scope (documented in
        // the annotation javadoc).
        val invalidateEntries = rawInvalidateEntries.mapNotNull { (method, targetName, keys) ->
            val target = memoizedMethods.firstOrNull { it.name == targetName }
            if (target == null) null
            else InvalidateEntryMethodInfo(method.name, method.desc, target.key, keys)
        }

        // Resolve @CacheInvalidate: both the legacy value() names AND the new
        // structured targets() list.
        val resolvedInvalidateMethods = rawInvalidates.map { raw ->
            val legacyResolved = raw.valueTargets.flatMap { targetName ->
                memoizedMethods.filter { it.name == targetName }.map { it.key }
            }
            val structured = raw.rawStructured.mapNotNull { node ->
                parseStructuredInvalidation(node, memoizedMethods, classNode)
            }
            InvalidateMethodInfo(raw.name, raw.descriptor, legacyResolved, structured)
        }

        if (memoizedMethods.isEmpty() && resolvedInvalidateMethods.isEmpty() && invalidateEntries.isEmpty()) {
            classNode.accept(nextVisitor)
            return
        }

        val internalName = classNode.name

        // Phase 2: Add fields
        classNode.fields.add(FieldNode(
            Opcodes.ACC_PRIVATE or Opcodes.ACC_SYNTHETIC,
            MANAGER_FIELD, MANAGER_DESC, null, null
        ))
        for (info in memoizedMethods) {
            classNode.fields.add(FieldNode(
                Opcodes.ACC_PRIVATE or Opcodes.ACC_SYNTHETIC,
                dispatcherFieldName(info.key), DISPATCHER_DESC, null, null
            ))
        }

        // Phase 3: Patch constructors
        for (method in classNode.methods) {
            if (method.name != "<init>") continue
            patchConstructor(method, internalName, memoizedMethods)
        }

        // Phase 4: Write modified ClassNode, then instrument method bodies
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        classNode.accept(cw)
        val modifiedBytes = cw.toByteArray()

        val cr = ClassReader(modifiedBytes)
        cr.accept(
            InstrumentingClassVisitor(api, nextVisitor, internalName, memoizedMethods, resolvedInvalidateMethods, invalidateEntries),
            ClassReader.EXPAND_FRAMES
        )
    }

    /**
     * Parse one @Invalidation AnnotationNode. Returns null and logs a warning if
     * the directive is malformed (missing target, missing key-builder method,
     * both keys and keyBuilder set, etc.). Callers filter nulls.
     */
    private fun parseStructuredInvalidation(
        node: AnnotationNode,
        memoizedMethods: List<MemoMethodInfo>,
        classNode: ClassNode
    ): StructuredInvalidation? {
        var targetName = ""
        var allEntries = false
        var keys = IntArray(0)
        var keyBuilder = ""
        var keyBuilderArgs = IntArray(0)

        node.values?.let { vals ->
            var i = 0
            while (i < vals.size - 1) {
                when (vals[i] as? String) {
                    "method" -> targetName = vals[i + 1] as? String ?: ""
                    "allEntries" -> allEntries = vals[i + 1] as? Boolean ?: false
                    "keys" -> {
                        val raw = vals[i + 1]
                        if (raw is List<*>) keys = raw.filterIsInstance<Int>().toIntArray()
                    }
                    "keyBuilder" -> keyBuilder = vals[i + 1] as? String ?: ""
                    "keyBuilderArgs" -> {
                        val raw = vals[i + 1]
                        if (raw is List<*>) keyBuilderArgs = raw.filterIsInstance<Int>().toIntArray()
                    }
                }
                i += 2
            }
        }

        if (targetName.isEmpty()) {
            System.err.println("[memoize] @Invalidation missing required 'method' field; skipping")
            return null
        }
        if (keys.isNotEmpty() && keyBuilder.isNotEmpty()) {
            System.err.println("[memoize] @Invalidation(method=$targetName) has both 'keys' and 'keyBuilder'; they are mutually exclusive. Skipping.")
            return null
        }

        val target = memoizedMethods.firstOrNull { it.name == targetName }
        if (target == null) {
            System.err.println("[memoize] @Invalidation target '$targetName' does not name a @Memoize method on ${classNode.name}; skipping")
            return null
        }

        val mode = when {
            allEntries -> InvalidationMode.FLUSH
            keyBuilder.isNotEmpty() -> InvalidationMode.KEY_BUILDER
            keys.isNotEmpty() -> InvalidationMode.KEYS
            else -> InvalidationMode.FLUSH
        }

        var builderDesc = ""
        var builderIsStatic = false
        var builderIsPrivate = false
        var builderReturnsObjectArray = false
        var effectiveBuilderArgs = keyBuilderArgs
        if (mode == InvalidationMode.KEY_BUILDER) {
            val m = classNode.methods.firstOrNull { it.name == keyBuilder && (it.access and Opcodes.ACC_ABSTRACT) == 0 }
            if (m == null) {
                System.err.println("[memoize] @Invalidation(method=$targetName, keyBuilder=$keyBuilder) — no method named '$keyBuilder' on ${classNode.name}; skipping")
                return null
            }
            builderDesc = m.desc
            builderIsStatic = (m.access and Opcodes.ACC_STATIC) != 0
            builderIsPrivate = (m.access and Opcodes.ACC_PRIVATE) != 0
            val retType = Type.getReturnType(m.desc)
            builderReturnsObjectArray = retType.descriptor == OBJECT_ARRAY_DESC

            // UX: when keyBuilderArgs is unspecified but the builder takes N
            // parameters, forward the first N parameters of the enclosing
            // method in declared order. This matches how most users mentally
            // model "pass the args through" without forcing them to spell out
            // indices that are usually {0} or {0,1}.
            if (effectiveBuilderArgs.isEmpty()) {
                val builderArity = Type.getArgumentTypes(m.desc).size
                if (builderArity > 0) {
                    effectiveBuilderArgs = IntArray(builderArity) { it }
                }
            }
        }

        return StructuredInvalidation(
            target.key, mode, keys, keyBuilder, builderDesc, builderIsStatic, builderIsPrivate, builderReturnsObjectArray, effectiveBuilderArgs
        )
    }

    private fun patchConstructor(method: MethodNode, internalName: String, memoizedMethods: List<MemoMethodInfo>) {
        val insns = method.instructions
        val returnInsns = mutableListOf<AbstractInsnNode>()

        var node = insns.first
        while (node != null) {
            if (node.opcode == Opcodes.RETURN) {
                returnInsns.add(node)
            }
            node = node.next
        }

        for (ret in returnInsns) {
            val initInsns = InsnList()

            // this.__memoCacheManager = new MemoCacheManager();
            initInsns.add(VarInsnNode(Opcodes.ALOAD, 0))
            initInsns.add(TypeInsnNode(Opcodes.NEW, MANAGER_INTERNAL))
            initInsns.add(InsnNode(Opcodes.DUP))
            initInsns.add(MethodInsnNode(Opcodes.INVOKESPECIAL, MANAGER_INTERNAL, "<init>", "()V", false))
            initInsns.add(FieldInsnNode(Opcodes.PUTFIELD, internalName, MANAGER_FIELD, MANAGER_DESC))

            for (info in memoizedMethods) {
                val fieldName = dispatcherFieldName(info.key)

                // this.__memoDispatcher_X = MemoDispatcher.create(key, maxSize, expireAfterWrite, eviction, threadSafety, recordStats[, autoMonitor, minHitRate, monitorWindow]);
                initInsns.add(VarInsnNode(Opcodes.ALOAD, 0))
                initInsns.add(LdcInsnNode(info.key))
                initInsns.add(LdcInsnNode(info.maxSize))
                initInsns.add(LdcInsnNode(info.expireAfterWrite))
                initInsns.add(LdcInsnNode(info.eviction))
                initInsns.add(LdcInsnNode(info.threadSafety))
                initInsns.add(InsnNode(if (info.recordStats) Opcodes.ICONST_1 else Opcodes.ICONST_0))

                if (info.autoMonitor) {
                    // Use 9-param create() with auto-monitor parameters
                    initInsns.add(InsnNode(Opcodes.ICONST_1)) // autoMonitor = true
                    initInsns.add(LdcInsnNode(info.minHitRate))
                    initInsns.add(LdcInsnNode(info.monitorWindow))
                    initInsns.add(MethodInsnNode(Opcodes.INVOKESTATIC, DISPATCHER_INTERNAL, "create",
                        "(Ljava/lang/String;IJLjava/lang/String;Ljava/lang/String;ZZDI)L$DISPATCHER_INTERNAL;", false))
                } else {
                    // Use 6-param create() (no auto-monitor overhead)
                    initInsns.add(MethodInsnNode(Opcodes.INVOKESTATIC, DISPATCHER_INTERNAL, "create",
                        "(Ljava/lang/String;IJLjava/lang/String;Ljava/lang/String;Z)L$DISPATCHER_INTERNAL;", false))
                }
                initInsns.add(FieldInsnNode(Opcodes.PUTFIELD, internalName, fieldName, DISPATCHER_DESC))

                // this.__memoCacheManager.register("X_XXXXX", this.__memoDispatcher_X_XXXXX);
                initInsns.add(VarInsnNode(Opcodes.ALOAD, 0))
                initInsns.add(FieldInsnNode(Opcodes.GETFIELD, internalName, MANAGER_FIELD, MANAGER_DESC))
                initInsns.add(LdcInsnNode(info.key))
                initInsns.add(VarInsnNode(Opcodes.ALOAD, 0))
                initInsns.add(FieldInsnNode(Opcodes.GETFIELD, internalName, fieldName, DISPATCHER_DESC))
                initInsns.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, MANAGER_INTERNAL, "register",
                    "(Ljava/lang/String;L$DISPATCHER_INTERNAL;)V", false))
            }

            insns.insertBefore(ret, initInsns)
        }
    }

    private fun dispatcherFieldName(key: String) = "__memoDispatcher_$key"
}

/**
 * Second-pass ClassVisitor that instruments @Memoize and @CacheInvalidate method bodies.
 */
class InstrumentingClassVisitor(
    api: Int,
    cv: ClassVisitor,
    private val internalName: String,
    private val memoizedMethods: List<MemoizeClassVisitor.MemoMethodInfo>,
    private val invalidateMethods: List<MemoizeClassVisitor.InvalidateMethodInfo>,
    private val invalidateEntryMethods: List<MemoizeClassVisitor.InvalidateEntryMethodInfo> = emptyList()
) : ClassVisitor(api, cv) {

    companion object {
        const val MANAGER_FIELD = MemoizeClassVisitor.MANAGER_FIELD
        const val MANAGER_INTERNAL = MemoizeClassVisitor.MANAGER_INTERNAL
        const val MANAGER_DESC = MemoizeClassVisitor.MANAGER_DESC
        const val DISPATCHER_INTERNAL = MemoizeClassVisitor.DISPATCHER_INTERNAL
        const val DISPATCHER_DESC = MemoizeClassVisitor.DISPATCHER_DESC
        const val CACHE_KEY_INTERNAL = MemoizeClassVisitor.CACHE_KEY_INTERNAL
    }

    // Map (name, descriptor) -> MemoMethodInfo for exact matching
    private val memoizedBySignature = memoizedMethods.associateBy { it.name to it.descriptor }
    // Match invalidator methods by (name, descriptor) too — overloaded mutators
    // with different @CacheInvalidate annotations now resolve independently.
    private val invalidateBySignature = invalidateMethods.associateBy { it.name to it.descriptor }
    private val invalidateEntryBySignature = invalidateEntryMethods.associateBy { it.name to it.descriptor }

    override fun visitMethod(
        access: Int, name: String, descriptor: String,
        signature: String?, exceptions: Array<String>?
    ): MethodVisitor? {
        val mv = super.visitMethod(access, name, descriptor, signature, exceptions) ?: return null

        // Match memoized methods by BOTH name and descriptor (handles overloads)
        val memoInfo = memoizedBySignature[name to descriptor]
        if (memoInfo != null) {
            return MemoizeMethodAdapter(api, mv, access, name, descriptor, memoInfo.key)
        }

        val invalidateInfo = invalidateBySignature[name to descriptor]
        if (invalidateInfo != null) {
            return InvalidateMethodAdapter(
                api, mv, access, name, descriptor,
                invalidateInfo.legacyTargets, invalidateInfo.structured
            )
        }

        val entryInfo = invalidateEntryBySignature[name to descriptor]
        if (entryInfo != null) {
            return InvalidateEntryMethodAdapter(api, mv, access, name, descriptor,
                entryInfo.targetMethodKey, entryInfo.keyIndices)
        }
        return mv
    }

    private fun dispatcherFieldName(key: String) = "__memoDispatcher_$key"

    inner class MemoizeMethodAdapter(
        api: Int, mv: MethodVisitor, access: Int,
        private val methodName: String, descriptor: String,
        private val methodKey: String
    ) : AdviceAdapter(api, mv, access, methodName, descriptor) {

        private var keyLocal = -1

        override fun onMethodEnter() {
            val dispField = dispatcherFieldName(methodKey)
            val returnType = Type.getReturnType(methodDesc)
            val argTypes = Type.getArgumentTypes(methodDesc)

            keyLocal = newLocal(Type.getObjectType(CACHE_KEY_INTERNAL))

            // Load dispatcher
            loadThis()
            mv.visitFieldInsn(Opcodes.GETFIELD, internalName, dispField, DISPATCHER_DESC)

            // Build args: new Object[]{boxed args...}
            push(argTypes.size)
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object")
            for (i in argTypes.indices) {
                mv.visitInsn(Opcodes.DUP)
                push(i)
                loadArg(i)
                box(argTypes[i])
                mv.visitInsn(Opcodes.AASTORE)
            }

            // Call buildKey
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, DISPATCHER_INTERNAL, "buildKey",
                "([Ljava/lang/Object;)L$CACHE_KEY_INTERNAL;", false)
            mv.visitVarInsn(Opcodes.ASTORE, keyLocal)

            // Call getIfCached
            loadThis()
            mv.visitFieldInsn(Opcodes.GETFIELD, internalName, dispField, DISPATCHER_DESC)
            mv.visitVarInsn(Opcodes.ALOAD, keyLocal)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, DISPATCHER_INTERNAL, "getIfCached",
                "(L$CACHE_KEY_INTERNAL;)Ljava/lang/Object;", false)

            val missLabel = Label()
            mv.visitInsn(Opcodes.DUP)
            mv.visitJumpInsn(Opcodes.IFNULL, missLabel)

            // Cache hit
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, DISPATCHER_INTERNAL, "unwrap",
                "(Ljava/lang/Object;)Ljava/lang/Object;", false)
            unboxAndReturn(returnType)

            // Cache miss
            mv.visitLabel(missLabel)
            mv.visitInsn(Opcodes.POP)
        }

        override fun onMethodExit(opcode: Int) {
            if (opcode == ATHROW || keyLocal < 0) return

            val dispField = dispatcherFieldName(methodKey)
            val returnType = Type.getReturnType(methodDesc)

            if (returnType.size == 2) mv.visitInsn(Opcodes.DUP2) else mv.visitInsn(Opcodes.DUP)
            box(returnType)

            val tempLocal = newLocal(Type.getObjectType("java/lang/Object"))
            mv.visitVarInsn(Opcodes.ASTORE, tempLocal)

            loadThis()
            mv.visitFieldInsn(Opcodes.GETFIELD, internalName, dispField, DISPATCHER_DESC)
            mv.visitVarInsn(Opcodes.ALOAD, keyLocal)
            mv.visitVarInsn(Opcodes.ALOAD, tempLocal)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, DISPATCHER_INTERNAL, "putInCache",
                "(L$CACHE_KEY_INTERNAL;Ljava/lang/Object;)Ljava/lang/Object;", false)
            mv.visitInsn(Opcodes.POP)
        }

        private fun unboxAndReturn(type: Type) {
            when (type.sort) {
                Type.BOOLEAN -> { cast("java/lang/Boolean"); mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false); mv.visitInsn(Opcodes.IRETURN) }
                Type.BYTE -> { cast("java/lang/Byte"); mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false); mv.visitInsn(Opcodes.IRETURN) }
                Type.CHAR -> { cast("java/lang/Character"); mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false); mv.visitInsn(Opcodes.IRETURN) }
                Type.SHORT -> { cast("java/lang/Short"); mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false); mv.visitInsn(Opcodes.IRETURN) }
                Type.INT -> { cast("java/lang/Integer"); mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false); mv.visitInsn(Opcodes.IRETURN) }
                Type.LONG -> { cast("java/lang/Long"); mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false); mv.visitInsn(Opcodes.LRETURN) }
                Type.FLOAT -> { cast("java/lang/Float"); mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false); mv.visitInsn(Opcodes.FRETURN) }
                Type.DOUBLE -> { cast("java/lang/Double"); mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false); mv.visitInsn(Opcodes.DRETURN) }
                else -> { mv.visitTypeInsn(Opcodes.CHECKCAST, type.internalName); mv.visitInsn(Opcodes.ARETURN) }
            }
        }

        private fun cast(internalName: String) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, internalName)
        }
    }

    /**
     * Injects cache invalidation before return instructions for methods marked
     * with @CacheInvalidate.
     *
     * Emits, in order:
     *   1. Legacy flush: manager.invalidate(legacyTargets) OR manager.invalidateAll()
     *      — when legacyTargets is non-empty OR both legacyTargets and structured are empty.
     *   2. One self-contained block per structured @Invalidation target.
     */
    inner class InvalidateMethodAdapter(
        api: Int, mv: MethodVisitor, access: Int, name: String, descriptor: String,
        private val legacyTargets: List<String>,
        private val structured: List<MemoizeClassVisitor.StructuredInvalidation>
    ) : AdviceAdapter(api, mv, access, name, descriptor) {

        override fun onMethodExit(opcode: Int) {
            if (opcode == ATHROW) return

            // Preserve prior semantics: a bare @CacheInvalidate (no value, no targets)
            // means "invalidate EVERY cache on this instance". With the structured
            // form, the user almost always wants targeted behavior, so invalidateAll
            // fires only when *both* lists are empty.
            val shouldInvalidateAll = legacyTargets.isEmpty() && structured.isEmpty()
            if (shouldInvalidateAll || legacyTargets.isNotEmpty()) {
                emitLegacyInvalidate(shouldInvalidateAll)
            }

            for (target in structured) {
                emitStructuredInvalidation(target)
            }
        }

        private fun emitLegacyInvalidate(invalidateAll: Boolean) {
            loadThis()
            mv.visitFieldInsn(Opcodes.GETFIELD, internalName, MANAGER_FIELD, MANAGER_DESC)
            val skipLabel = Label()
            mv.visitInsn(Opcodes.DUP)
            mv.visitJumpInsn(Opcodes.IFNULL, skipLabel)

            if (invalidateAll) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MANAGER_INTERNAL, "invalidateAll", "()V", false)
            } else {
                push(legacyTargets.size)
                mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String")
                for (i in legacyTargets.indices) {
                    mv.visitInsn(Opcodes.DUP)
                    push(i)
                    mv.visitLdcInsn(legacyTargets[i])
                    mv.visitInsn(Opcodes.AASTORE)
                }
                mv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL, MANAGER_INTERNAL, "invalidate",
                    "([Ljava/lang/String;)V", false
                )
            }

            val endLabel = Label()
            mv.visitJumpInsn(Opcodes.GOTO, endLabel)
            mv.visitLabel(skipLabel)
            mv.visitInsn(Opcodes.POP)
            mv.visitLabel(endLabel)
        }

        private fun emitStructuredInvalidation(t: MemoizeClassVisitor.StructuredInvalidation) {
            // All three modes share the same load-manager + null-guard preamble.
            loadThis()
            mv.visitFieldInsn(Opcodes.GETFIELD, internalName, MANAGER_FIELD, MANAGER_DESC)
            val skipLabel = Label()
            mv.visitInsn(Opcodes.DUP)
            mv.visitJumpInsn(Opcodes.IFNULL, skipLabel)

            when (t.mode) {
                MemoizeClassVisitor.InvalidationMode.FLUSH -> emitFlushOne(t.targetMethodKey)
                MemoizeClassVisitor.InvalidationMode.KEYS -> emitEntryByKeys(t.targetMethodKey, t.keyIndices)
                MemoizeClassVisitor.InvalidationMode.KEY_BUILDER -> emitEntryByKeyBuilder(t)
            }

            val endLabel = Label()
            mv.visitJumpInsn(Opcodes.GOTO, endLabel)
            mv.visitLabel(skipLabel)
            mv.visitInsn(Opcodes.POP)
            mv.visitLabel(endLabel)
        }

        /** Stack in: manager. Stack out: empty after invalidate call. */
        private fun emitFlushOne(methodKey: String) {
            push(1)
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String")
            mv.visitInsn(Opcodes.DUP)
            push(0)
            mv.visitLdcInsn(methodKey)
            mv.visitInsn(Opcodes.AASTORE)
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL, MANAGER_INTERNAL, "invalidate",
                "([Ljava/lang/String;)V", false
            )
        }

        /** Stack in: manager. Stack out: empty after invalidateEntry call. */
        private fun emitEntryByKeys(methodKey: String, keyIndices: IntArray) {
            val argTypes = Type.getArgumentTypes(methodDesc)
            mv.visitLdcInsn(methodKey)
            push(keyIndices.size)
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object")
            for (i in keyIndices.indices) {
                val paramIdx = keyIndices[i]
                if (paramIdx < 0 || paramIdx >= argTypes.size) continue
                mv.visitInsn(Opcodes.DUP)
                push(i)
                loadArg(paramIdx)
                box(argTypes[paramIdx])
                mv.visitInsn(Opcodes.AASTORE)
            }
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL, MANAGER_INTERNAL, "invalidateEntry",
                "(Ljava/lang/String;[Ljava/lang/Object;)V", false
            )
        }

        /**
         * Stack in: manager. Stack out: empty after invalidateEntry call.
         *
         * Two paths depending on keyBuilder's return type:
         *   - Object[]: call builder, pass its return value straight through.
         *   - anything else: pre-allocate a 1-slot Object[], stash builder's
         *     return into slot 0 (with boxing for primitives).
         */
        private fun emitEntryByKeyBuilder(t: MemoizeClassVisitor.StructuredInvalidation) {
            mv.visitLdcInsn(t.targetMethodKey)

            val builderArgTypes = Type.getArgumentTypes(t.keyBuilderDesc)
            val builderRetType = Type.getReturnType(t.keyBuilderDesc)

            if (t.keyBuilderReturnsObjectArray) {
                // Call builder, its Object[] return becomes the args tuple.
                if (!t.keyBuilderIsStatic) loadThis()
                loadBuilderArgs(t.keyBuilderArgs, builderArgTypes)
                invokeBuilder(t)
                // Stack: manager, methodKey, Object[]
            } else {
                // Build Object[1] on stack, dup it, load index 0, run builder,
                // box the result, aastore into slot 0.
                push(1)
                mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object")
                mv.visitInsn(Opcodes.DUP)
                push(0)
                if (!t.keyBuilderIsStatic) loadThis()
                loadBuilderArgs(t.keyBuilderArgs, builderArgTypes)
                invokeBuilder(t)
                // Stack: manager, methodKey, Object[], Object[], 0, <ret>
                box(builderRetType)
                mv.visitInsn(Opcodes.AASTORE)
                // Stack: manager, methodKey, Object[]
            }

            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL, MANAGER_INTERNAL, "invalidateEntry",
                "(Ljava/lang/String;[Ljava/lang/Object;)V", false
            )
        }

        private fun loadBuilderArgs(builderArgs: IntArray, builderArgTypes: Array<Type>) {
            val enclosingArgTypes = Type.getArgumentTypes(methodDesc)
            for (i in builderArgs.indices) {
                val paramIdx = builderArgs[i]
                if (paramIdx < 0 || paramIdx >= enclosingArgTypes.size) {
                    // Push a default zero/null of the expected builder arg type so
                    // the stack stays valid — a misconfigured index would otherwise
                    // crash verification at class load.
                    val expected = if (i < builderArgTypes.size) builderArgTypes[i] else Type.getType("Ljava/lang/Object;")
                    pushDefault(expected)
                    continue
                }
                loadArg(paramIdx)
                // If the builder expects Object (or any reference) but we have a
                // primitive in the enclosing method, box it. AdviceAdapter's
                // own loadArg doesn't auto-box for us.
                val have = enclosingArgTypes[paramIdx]
                val want = if (i < builderArgTypes.size) builderArgTypes[i] else have
                if (have.sort != Type.OBJECT && have.sort != Type.ARRAY && want.sort == Type.OBJECT) {
                    box(have)
                }
            }
        }

        private fun pushDefault(t: Type) {
            when (t.sort) {
                Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT ->
                    mv.visitInsn(Opcodes.ICONST_0)
                Type.LONG -> mv.visitInsn(Opcodes.LCONST_0)
                Type.FLOAT -> mv.visitInsn(Opcodes.FCONST_0)
                Type.DOUBLE -> mv.visitInsn(Opcodes.DCONST_0)
                else -> mv.visitInsn(Opcodes.ACONST_NULL)
            }
        }

        private fun invokeBuilder(t: MemoizeClassVisitor.StructuredInvalidation) {
            // JVMS: INVOKEVIRTUAL must not resolve a private method. Use
            // INVOKESPECIAL for private instance helpers (the usual case for
            // key-builder methods) and INVOKESTATIC for statics.
            val opcode = when {
                t.keyBuilderIsStatic -> Opcodes.INVOKESTATIC
                t.keyBuilderIsPrivate -> Opcodes.INVOKESPECIAL
                else -> Opcodes.INVOKEVIRTUAL
            }
            mv.visitMethodInsn(opcode, internalName, t.keyBuilderName, t.keyBuilderDesc, false)
        }
    }

    /**
     * Injects single-entry cache eviction before each normal return of a
     * method annotated with {@code @InvalidateCacheEntry}. Builds an
     * {@code Object[]} from the specified parameter indices of the enclosing
     * method (boxing each as needed) and forwards it to
     * {@code MemoCacheManager.invalidateEntry(methodKey, args)}.
     */
    inner class InvalidateEntryMethodAdapter(
        api: Int, mv: MethodVisitor, access: Int, name: String, descriptor: String,
        private val targetMethodKey: String,
        private val keyIndices: IntArray
    ) : AdviceAdapter(api, mv, access, name, descriptor) {

        override fun onMethodExit(opcode: Int) {
            if (opcode == ATHROW) return

            val argTypes = Type.getArgumentTypes(methodDesc)

            // Load manager; bail if null (instance still under construction).
            loadThis()
            mv.visitFieldInsn(Opcodes.GETFIELD, internalName, MANAGER_FIELD, MANAGER_DESC)
            val skipLabel = Label()
            mv.visitInsn(Opcodes.DUP)
            mv.visitJumpInsn(Opcodes.IFNULL, skipLabel)

            // Push target methodKey (resolved at build time).
            mv.visitLdcInsn(targetMethodKey)

            // Build Object[] from enclosing method's selected parameters.
            push(keyIndices.size)
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object")
            for (i in keyIndices.indices) {
                val paramIdx = keyIndices[i]
                // Silently clamp out-of-range indices so a user typo doesn't
                // crash the build; the resulting null slot simply misses at
                // runtime, same as any mismatched key.
                if (paramIdx < 0 || paramIdx >= argTypes.size) continue
                mv.visitInsn(Opcodes.DUP)
                push(i)
                loadArg(paramIdx)
                box(argTypes[paramIdx])
                mv.visitInsn(Opcodes.AASTORE)
            }

            // manager.invalidateEntry(String, Object[])
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL, MANAGER_INTERNAL, "invalidateEntry",
                "(Ljava/lang/String;[Ljava/lang/Object;)V", false
            )

            val endLabel = Label()
            mv.visitJumpInsn(Opcodes.GOTO, endLabel)
            mv.visitLabel(skipLabel)
            mv.visitInsn(Opcodes.POP)
            mv.visitLabel(endLabel)
        }
    }
}
