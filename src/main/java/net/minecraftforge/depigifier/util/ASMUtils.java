package net.minecraftforge.depigifier.util;

import org.objectweb.asm.*;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.FileSystem;
import java.util.Arrays;

public class ASMUtils
{

    private ASMUtils()
    {
        throw new IllegalStateException("Can not instantiate an instance of: ASMUtils. This is a utility class");
    }

    public static ASMMethodData getMethod(final FileSystem jarFs, String className, String methodName, String methodDescriptor) throws RuntimeException
    {
        try
        {
            ClassReader classReader = new ClassReader(jarFs.getPath(className + ".class").toUri().toURL().openStream());
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            TraceClassVisitor traceClassVisitor = new TraceClassVisitor(null, new SourceCodeTextifier(), printWriter);
            MethodSelectorVisitor methodSelectorVisitor = new MethodSelectorVisitor(traceClassVisitor, methodName, methodDescriptor);
            classReader.accept(methodSelectorVisitor, ClassReader.SKIP_DEBUG);

            final String result = stringWriter.toString().trim();

            if (result.isEmpty())
            {
                if (methodSelectorVisitor.isFound()) {
                    return new ASMMethodData(toList(""), methodSelectorVisitor.getAccess(), methodSelectorVisitor.isFound(), className, methodName, methodDescriptor);
                }

                final SuperClassReadingClassVisitor superClassReadingClassVisitor = new SuperClassReadingClassVisitor();
                classReader.accept(superClassReadingClassVisitor, ClassReader.SKIP_DEBUG);
                final String superClassName = superClassReadingClassVisitor.getSuperClass();

                if (superClassName != null && !superClassName.equals("java/lang/Object"))
                {
                    return getMethod(jarFs, superClassName, methodName, methodDescriptor);
                }
                else
                {
                    return new ASMMethodData(new String[0], 0, false, className, methodName, methodDescriptor);
                }
            }

            return new ASMMethodData(toList(result), methodSelectorVisitor.getAccess(), methodSelectorVisitor.isFound(), className, methodName, methodDescriptor);
        }
        catch (FileNotFoundException e) {
            return new ASMMethodData(toList( ""), 0, false, className, methodName, methodDescriptor);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to get method body for " + className + "." + methodName + "(" + methodDescriptor + ")", e);
        }
    }

    public static String[] getInterfaces(final FileSystem jarFs, String className) throws RuntimeException {
        try
        {
            ClassReader classReader = new ClassReader(jarFs.getPath(className + ".class").toUri().toURL().openStream());
            InterfaceReadingClassVisitor interfaceReadingClassVisitor = new InterfaceReadingClassVisitor();
            classReader.accept(interfaceReadingClassVisitor, ClassReader.SKIP_DEBUG);

            return interfaceReadingClassVisitor.getInterfaces();
        }
        catch (FileNotFoundException e) {
            return new String[0];
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to get interfaces for " + className, e);
        }
    }

    public static String getSuper(final FileSystem jarFs, String className) throws RuntimeException {
        try
        {
            ClassReader classReader = new ClassReader(jarFs.getPath(className + ".class").toUri().toURL().openStream());
            SuperClassReadingClassVisitor superClassReadingClassVisitor = new SuperClassReadingClassVisitor();
            classReader.accept(superClassReadingClassVisitor, ClassReader.SKIP_DEBUG);

            return superClassReadingClassVisitor.getSuperClass();
        }
        catch (FileNotFoundException e) {
            return "java/lang/Object";
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to get interfaces for " + className, e);
        }
    }

    private static String[] toList(String str)
    {
        //won't work correctly for all OSs /TODO: Figure this out
        String[] operations = str.split("[" + "\n" + "]");

        for (int i = 0; i < operations.length; ++i)
        {
            operations[i] = operations[i].trim();
        }

        return operations;
    }

    private static class MethodSelectorVisitor extends ClassVisitor
    {
        private final String methodName;
        private final String methodDescriptor;

        private boolean found = false;
        private int access = 0;

        public MethodSelectorVisitor(ClassVisitor cv, String methodName, String methodDescriptor)
        {
            super(Opcodes.ASM9, cv);
            this.methodName = methodName;
            this.methodDescriptor = methodDescriptor;
        }

        @Override
        public MethodVisitor visitMethod(
          int access, String name, String desc,
          String signature, String[] exceptions)
        {

            if (methodName.equals(name))
            {
                if (methodDescriptor == null)
                {
                    this.found = true;
                    this.access = access;
                    return new MaxVisitFilterMethodVisitor(super.visitMethod(access, name, desc, signature, exceptions));
                }

                if (methodDescriptor.equals(desc))
                {
                    this.found = true;
                    this.access = access;
                    return new MaxVisitFilterMethodVisitor(super.visitMethod(access, name, desc, signature, exceptions));
                }
            }

            return null;
        }

        public boolean isFound()
        {
            return found;
        }

        public int getAccess()
        {
            return access;
        }
    }

    private static class MaxVisitFilterMethodVisitor extends MethodVisitor
    {
        public MaxVisitFilterMethodVisitor(MethodVisitor mv)
        {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals)
        {
        }
    }

    private static class SourceCodeTextifier extends Printer
    {
        public SourceCodeTextifier()
        {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(
          final int version,
          final int access,
          final String name,
          final String signature,
          final String superName,
          final String[] interfaces)
        {
        }

        @Override
        public void visitSource(final String file, final String debug)
        {
        }

        @Override
        public void visitNestHost(final String nestHost)
        {
        }

        @Override
        public void visitOuterClass(
          final String owner,
          final String name,
          final String desc)
        {
        }

        @Override
        public Textifier visitClassAnnotation(
          final String desc,
          final boolean visible)
        {
            return new Textifier();
        }

        @Override
        public void visitClassAttribute(final Attribute attr)
        {
        }

        @Override
        public void visitNestMember(final String nestMember)
        {
        }

        @Override
        public void visitInnerClass(
          final String name,
          final String outerName,
          final String innerName,
          final int access)
        {
        }

        @Override
        public Printer visitRecordComponent(final String name, final String descriptor, final String signature)
        {
            return new Textifier();
        }

        @Override
        public Textifier visitField(
          final int access,
          final String name,
          final String desc,
          final String signature,
          final Object value)
        {
            return new Textifier();
        }

        @Override
        public Textifier visitMethod(
          final int access,
          final String name,
          final String desc,
          final String signature,
          final String[] exceptions)
        {
            Textifier t = new Textifier();
            text.add(t.getText());
            return t;
        }

        @Override
        public void visitClassEnd()
        {
        }

        @Override
        public void visit(final String name, final Object value)
        {
        }

        @Override
        public void visitEnum(
          final String name,
          final String desc,
          final String value)
        {
        }

        @Override
        public Textifier visitAnnotation(
          final String name,
          final String desc)
        {
            return new Textifier();
        }

        @Override
        public Textifier visitArray(
          final String name)
        {
            return new Textifier();
        }

        @Override
        public void visitAnnotationEnd()
        {
        }

        @Override
        public Textifier visitFieldAnnotation(
          final String desc,
          final boolean visible)
        {
            return new Textifier();
        }

        @Override
        public void visitFieldAttribute(final Attribute attr)
        {
            visitAttribute(attr);
        }

        @Override
        public void visitFieldEnd()
        {
        }

        @Override
        public Textifier visitAnnotationDefault()
        {
            return new Textifier();
        }

        @Override
        public Textifier visitMethodAnnotation(
          final String desc,
          final boolean visible)
        {
            return new Textifier();
        }

        @Override
        public Textifier visitParameterAnnotation(
          final int parameter,
          final String desc,
          final boolean visible)
        {
            return new Textifier();
        }

        @Override
        public void visitMethodAttribute(final Attribute attr)
        {
        }

        @Override
        public void visitCode()
        {
        }

        @Override
        public void visitFrame(
          final int type,
          final int nLocal,
          final Object[] local,
          final int nStack,
          final Object[] stack)
        {
        }

        @Override
        public void visitInsn(final int opcode)
        {
        }

        @Override
        public void visitIntInsn(final int opcode, final int operand)
        {
        }

        @Override
        public void visitVarInsn(final int opcode, final int var)
        {
        }

        @Override
        public void visitTypeInsn(final int opcode, final String type)
        {
        }

        @Override
        public void visitFieldInsn(
          final int opcode,
          final String owner,
          final String name,
          final String desc)
        {
        }

        @Override
        public void visitMethodInsn(
          final int opcode,
          final String owner,
          final String name,
          final String desc)
        {
        }

        @Override
        public void visitInvokeDynamicInsn(
          String name,
          String desc,
          Handle bsm,
          Object... bsmArgs)
        {
        }

        @Override
        public void visitJumpInsn(final int opcode, final Label label)
        {
        }

        @Override
        public void visitLabel(final Label label)
        {
        }

        @Override
        public void visitLdcInsn(final Object cst)
        {
        }

        @Override
        public void visitIincInsn(final int var, final int increment)
        {
        }

        @Override
        public void visitTableSwitchInsn(
          final int min,
          final int max,
          final Label dflt,
          final Label... labels)
        {
        }

        @Override
        public void visitLookupSwitchInsn(
          final Label dflt,
          final int[] keys,
          final Label[] labels)
        {
        }

        @Override
        public void visitMultiANewArrayInsn(final String desc, final int dims)
        {
        }

        @Override
        public void visitTryCatchBlock(
          final Label start,
          final Label end,
          final Label handler,
          final String type)
        {
        }

        @Override
        public void visitLocalVariable(
          final String name,
          final String desc,
          final String signature,
          final Label start,
          final Label end,
          final int index)
        {
        }

        @Override
        public void visitLineNumber(final int line, final Label start)
        {
        }

        @Override
        public void visitMaxs(final int maxStack, final int maxLocals)
        {
        }

        @Override
        public void visitMethodEnd()
        {
        }

        public void visitAttribute(final Attribute attr)
        {
        }

        @Override
        public void visitPermittedSubclass(final String permittedSubclass)
        {
        }
    }

    private static final class SuperClassReadingClassVisitor extends ClassVisitor
    {
        private String superClass = "";

        public SuperClassReadingClassVisitor()
        {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces)
        {
            superClass = superName;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        public String getSuperClass()
        {
            return superClass;
        }
    }

    private static final class InterfaceReadingClassVisitor extends ClassVisitor
    {
        private String[] interfaces = new String[0];

        public InterfaceReadingClassVisitor()
        {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces)
        {
            this.interfaces = interfaces;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        public String[] getInterfaces()
        {
            return interfaces;
        }
    }

    public static final class ASMMethodData {
        private final String[] byteCode;
        private final int accessFlags;
        private final boolean wasFound;

        private final String className;
        private final String methodName;
        private final String methodDescriptor;

        private ASMMethodData(
          final String[] byteCode,
          final int accessFlags,
          final boolean wasFound,
          final String className,
          final String methodName,
          final String methodDescriptor)
        {
            this.byteCode = byteCode;
            this.accessFlags = accessFlags;
            this.wasFound = wasFound;
            this.className = className;
            this.methodName = methodName;
            this.methodDescriptor = methodDescriptor;
        }

        @Override
        public boolean equals(final Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (!(o instanceof ASMMethodData))
            {
                return false;
            }

            final ASMMethodData that = (ASMMethodData) o;

            // Probably incorrect - comparing Object[] arrays with Arrays.equals
            return Arrays.equals(getByteCode(), that.getByteCode());
        }

        @Override
        public int hashCode()
        {
            return Arrays.hashCode(getByteCode());
        }

        public String[] getByteCode()
        {
            return byteCode;
        }

        public int getAccessFlags()
        {
            return accessFlags;
        }

        public boolean wasFound()
        {
            return wasFound;
        }

        public String getClassName()
        {
            return className;
        }

        public String getMethodName()
        {
            return methodName;
        }

        public String getMethodDescriptor()
        {
            return methodDescriptor;
        }
    }
}
