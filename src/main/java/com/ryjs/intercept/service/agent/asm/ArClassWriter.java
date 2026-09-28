package com.ryjs.intercept.service.agent.asm;

import com.ryjs.intercept.service.agent.TypeIndex;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;


public final class ArClassWriter extends ClassWriter {

    private final ClassLoader loader;
    private final TypeIndex index;

    public ArClassWriter(ClassReader cr, ClassLoader loader, TypeIndex index) {
        super(cr, COMPUTE_FRAMES | COMPUTE_MAXS);
        this.loader = loader;
        this.index = index;
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        try {
            return index.commonSuperClass(type1, type2, loader);
        } catch (Throwable e) {
            return "java/lang/Object";
        }
    }
}
