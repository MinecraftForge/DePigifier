package net.minecraftforge.depigifier.model;

import org.objectweb.asm.Type;

import net.minecraftforge.depigifier.IMapper;

public class Method {
    private final Class owner;
    private final String oldName;
    private final String desc;
    private final Type[] args;
    private final Type retType;

    private String newName;
    private int start = -1;
    private int end = -1;

    public Method(final Class owner, final String name, final String desc) {
        this.owner = owner;
        this.oldName = name;
        this.newName = name;
        this.desc = desc;
        this.args = Type.getArgumentTypes(desc);
        this.retType = Type.getReturnType(desc);

        for (Type arg : args) {
            if (arg.getSort() == Type.ARRAY)
                arg = arg.getElementType();
            if (arg.getSort() == Type.OBJECT)
                getOwner().addReference(arg.getInternalName(), this);
        }
    }

    public Method setLines(int start, int end) {
        this.start = start;
        this.end = end;
        return this;
    }

    public Method rename(String newName) {
        getOwner().renameMethod(this, newName);
        this.newName = newName;
        return this;
    }

    @Override
    public String toString() {
        return (hasLines() ? "(" + getStart() + ":" + getEnd() + ") " : "") + getOwner().getOldName() + "." + getOldName() + getOldDesc();
    }

    public String getOldDesc() {
        return desc;
    }

    public String getNewDesc(IMapper mapper) {
        return mapper.mapDescriptor(args, retType);
    }

    public Class getOwner() {
        return owner;
    }

    public String getOldName() {
        return oldName;
    }

    public String getNewName() {
        return newName;
    }

    public boolean hasLines() {
        return start != -1 && end != -1;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }
}
