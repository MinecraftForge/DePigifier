package net.minecraftforge.depigifier;

import org.objectweb.asm.Type;

public interface IMapper {
    String mapClass(String cls);
    String mapField(String cls, String field);
    String mapMethod(String cls, String method, String desc);

    default String mapDescriptor(String desc) {
        return mapDescriptor(Type.getArgumentTypes(desc), Type.getReturnType(desc));
    }

    default String mapDescriptor(Type[] args, Type ret) {
        StringBuilder buf = new StringBuilder();
        buf.append('(');
        for (Type arg : args) {
            if (arg.getSort() == Type.ARRAY) {
                for (int x = 0; x < arg.getDimensions(); x++)
                    buf.append('[');
                arg = arg.getElementType();
            }
            if (arg.getSort() == Type.OBJECT)
                buf.append('L').append(mapClass(arg.getInternalName())).append(';');
            else
                buf.append(arg.getDescriptor());
        }
        buf.append(')');

        if (ret.getSort() == Type.ARRAY) {
            for (int x = 0; x < ret.getDimensions(); x++)
                buf.append('[');
            ret = ret.getElementType();
        }
        if (ret.getSort() == Type.OBJECT)
            buf.append('L').append(mapClass(ret.getInternalName())).append(';');
        else
            buf.append(ret.getDescriptor());

        return buf.toString();
    }
}
