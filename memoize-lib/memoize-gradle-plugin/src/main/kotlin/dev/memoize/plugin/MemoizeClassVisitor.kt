package dev.memoize.plugin

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
        const val MEMOIZE_DESC = "Ldev/memoize/annotations/Memoize;"
        const val CACHE_INVALIDATE_DESC = "Ldev/memoize/annotations/CacheInvalidate;"
        const val INVALIDATE_ENTRY_DESC = "Ldev/memoize/annotations/InvalidateCacheEntry;"
        const val MANAGER_FIELD = "__memoCacheManager"
        const val MANAGER_INTERNAL = "dev/memoize/runtime/MemoCacheManager"
        const val MANAGER_DESC = "Ldev/memoize/runtime/MemoCacheManager;"
        const val DISPATCHER_INTERNAL = "dev/memoize/runtime/MemoDispatcher"
        const val DISPATCHER_DESC = "Ldev/memoize/runtime/MemoDispatcher;"
        const val CACHE_KEY_INTERNAL = "dev/memoize/runtime/CacheKeyWrapper"
        const val CACHE_KEY_DESC = "Ldev/memoize/runtime/CacheKeyWrapper;"

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
    data class InvalidateMethodInfo(
        val name: String,
        val targets: List<String>  // user-specified method names (empty = invalidate all)
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

    override fun visitEnd() {
        // super.visitEnd() forwards to classNode.visitEnd() via the ClassVisitor
        // delegation chain. We deliberately do NOT also forward to nextVisitor
        // here — we take ownership from this point and decide below whether to
        // emit the class unchanged or run the instrumentation pipeline.
        super.visitEnd()

        // Phase 1: Scan for annotated methods
        val memoizedMethods = mutableListOf<MemoMethodInfo>()
        val invalidateMethods = mutableListOf<InvalidateMethodInfo>()
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
                        val targets = mutableListOf<String>()
                        if (ann.values != null) {
                            val values = ann.values
                            var i = 0
                            while (i < values.size - 1) {
                                if (values[i] == "value") {
                                    val v = values[i + 1]
                                    if (v is List<*>) {
                                        targets.addAll(v.filterIsInstance<String>())
                                    }
                                }
                                i += 2
                            }
                        }
                        invalidateMethods.add(InvalidateMethodInfo(method.name, targets))
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

        if (memoizedMethods.isEmpty() && invalidateMethods.isEmpty() && invalidateEntries.isEmpty()) {
            classNode.accept(nextVisitor)
            return
        }

        // Resolve @CacheInvalidate targets: map user-specified method names
        // to the actual methodKeys (handles overloaded methods)
        val resolvedInvalidateMethods = invalidateMethods.map { info ->
            if (info.targets.isEmpty()) {
                info // empty = invalidateAll, no resolution needed
            } else {
                val resolvedKeys = info.targets.flatMap { targetName ->
                    // Find all memoized methods matching this name (all overloads)
                    memoizedMethods.filter { it.name == targetName }.map { it.key }
                }
                InvalidateMethodInfo(info.name, resolvedKeys)
            }
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
    private val invalidateMap = invalidateMethods.associateBy { it.name }
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

        val invalidateInfo = invalidateMap[name]
        if (invalidateInfo != null) {
            return InvalidateMethodAdapter(api, mv, access, name, descriptor, invalidateInfo.targets)
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
     * Injects cache invalidation before return instructions.
     * targets contains resolved methodKeys (already expanded for overloads).
     * Empty targets = invalidateAll().
     */
    inner class InvalidateMethodAdapter(
        api: Int, mv: MethodVisitor, access: Int, name: String, descriptor: String,
        private val targets: List<String>
    ) : AdviceAdapter(api, mv, access, name, descriptor) {

        override fun onMethodExit(opcode: Int) {
            if (opcode == ATHROW) return

            loadThis()
            mv.visitFieldInsn(Opcodes.GETFIELD, internalName, MANAGER_FIELD, MANAGER_DESC)
            val skipLabel = Label()
            mv.visitInsn(Opcodes.DUP)
            mv.visitJumpInsn(Opcodes.IFNULL, skipLabel)

            if (targets.isEmpty()) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MANAGER_INTERNAL, "invalidateAll", "()V", false)
            } else {
                push(targets.size)
                mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String")
                for (i in targets.indices) {
                    mv.visitInsn(Opcodes.DUP)
                    push(i)
                    mv.visitLdcInsn(targets[i])  // resolved methodKey, e.g., "search_0a3f2"
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
