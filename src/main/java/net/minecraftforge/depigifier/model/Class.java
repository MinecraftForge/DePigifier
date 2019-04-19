package net.minecraftforge.depigifier.model;

import java.util.*;

public class Class {
    private final String proguardName;
    private final String obfName;
    private final Map<String,Field> fields = new HashMap<>();
    private final Map<String,Method> methods = new HashMap<>();
    private String srgName;

    public Class(final String proguardName, final String obfName) {
        this.proguardName = proguardName;
        this.obfName = obfName;
    }

    public String getProguardName() {
        return proguardName;
    }

    public String getObfName() {
        return obfName;
    }

    @Override
    public String toString() {
        return proguardName +"->"+obfName+"->"+srgName;
    }

    public Method addMethod(final Method method) {
        methods.put(method.getMethodSignature(), method);
        return method;
    }

    public Field addField(final Field field) {
        fields.put(field.getProguardName(), field);
        return field;
    }

    public Set<String> getFieldNames() {
        return fields.keySet();
    }

    public Set<String> getMethodSignatures() {
        return methods.keySet();
    }

    public void setSrgName(final String srgName) {
        this.srgName = srgName;
    }

    public String getSrgName() {
        return srgName;
    }
}
