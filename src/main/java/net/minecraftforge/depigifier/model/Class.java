package net.minecraftforge.depigifier.model;

import java.util.*;

import net.minecraftforge.depigifier.IMapper;

public class Class {
    private final Map<String, Field> o2nFields = new HashMap<>();
    private final Map<String, Field> n2oFields = new HashMap<>();
    private final Map<String, Method> o2nMethods = new HashMap<>();
    private final Map<String, Method> n2oMethods = new HashMap<>();
    private final Map<String, Set<Method>> references = new HashMap<>();

    private final Tree tree;
    private final String oldName;
    private String newName;

    Class(final Tree tree, final String name) {
        this.tree = tree;
        this.oldName = name;
        this.newName = name;
    }

    @Override
    public String toString() {
        return "CL: " + oldName + " " + newName;
    }

    public Class rename(String newName) {
        this.tree.renameClass(this, newName);
        this.newName = newName;
        return this;
    }

    public String getOldName() {
        return oldName;
    }

    public String getNewName() {
        return newName;
    }

    public Field getField(String name) {
        return o2nFields.computeIfAbsent(name, k -> new Field(this, name));
    }

    public Field tryField(String name) {
        return o2nFields.get(name);
    }

    //Warning: Data is not copied, Do not modify.
    public Collection<Field> getFields() {
        return o2nFields.values();
    }

    //Warning: Data is not copied, Do not modify.
    public Set<String> getFieldNames() {
        return o2nFields.keySet();
    }

    public Method getMethod(String name, String desc) {
        return o2nMethods.computeIfAbsent(name + desc, k -> new Method(this, name, desc));
    }

    public Method tryMethod(String name, String desc) {
        return o2nMethods.get(name + desc);
    }

    //Warning: Data is not copied, Do not modify.
    public Collection<Method> getMethods() {
        return o2nMethods.values();
    }

    //Warning: Data is not copied, Do not modify.
    public Set<String> getMethodSignatures() {
        return o2nMethods.keySet();
    }

    public String mapField(String field) {
        Field fld = o2nFields.get(field);
        return fld == null ? field : fld.getNewName();
    }

    public String mapMethod(String method, String desc) {
        Method mtd = o2nMethods.get(method + desc);
        return mtd == null ? method : mtd.getNewName();
    }

    //Package Private things are notifications from other methods, use those instead.

    void renameField(Field field, String newName) {
        //TODO: Add check to not overwrite existing?
        n2oFields.remove(field.getNewName());
        n2oFields.put(newName, field);
    }

    void renameMethod(Method method, String newName) {
        //TODO: Add check to not overwrite existing?
        String desc = method.getNewDesc(tree);
        n2oMethods.remove(method.getNewName() + desc);
        n2oMethods.put(newName + desc, method);
    }

    void addReference(String cls, Method method) {
        references.computeIfAbsent(cls, k -> new HashSet<>()).add(method);
        tree.addReference(cls, this);
    }

    void classRenamed(Class cls, IMapper mapper) {
        references.getOrDefault(cls.getOldName(), Collections.emptySet()).forEach(m -> {
            n2oMethods.remove(m.getNewName() + m.getNewDesc(tree));
            n2oMethods.put(m.getNewName() + m.getNewDesc(mapper), m);
        });
    }
}
