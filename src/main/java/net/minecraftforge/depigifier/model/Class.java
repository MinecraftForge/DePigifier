package net.minecraftforge.depigifier.model;

import net.minecraftforge.depigifier.ClassLookup;
import net.minecraftforge.depigifier.ProguardFile;

import java.util.*;

public class Class {
    private final String proguardName;
    private final String obfName;
    private String srgName;
    private final Map<String,Field> fields = new HashMap<>();
    private final Map<String,Field> obfFields = new HashMap<>();
    private final Map<String,Method> methods = new HashMap<>();
    private final Map<String,Method> obfMethods = new HashMap<>();
    private Class matchedOldValue;

    public Class(final String proguardName, final String obfName) {
        this.proguardName = ClassLookup.transformInternalName(proguardName);
        this.obfName = ClassLookup.transformInternalName(obfName);
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

    public Method addMethod(final Method method, final ProguardFile proguardFile) {
        methods.put(method.getMethodSignature(), method);
        method.setOwner(this);
        return method;
    }

    public Field addField(final Field field, final ProguardFile proguardFile) {
        fields.put(field.getProguardName(), field);
        field.setOwner(this);
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

    public Method findObfMethod(final String obfName, final String obfSignature) {
        return obfMethods.get(obfName+obfSignature);
    }

    public Method getMethod(final String signature) {
        return methods.get(signature);
    }

    public void buildObfLookups(final ProguardFile proguardFile) {
        methods.values().forEach(m->obfMethods.put(m.getObfSignature(proguardFile), m));
    }

    public Class getMatchedOldValue() {
        return matchedOldValue;
    }

    public void setMatchedOldValue(final Class matchedOldValue) {
        this.matchedOldValue = matchedOldValue;
        this.srgName = matchedOldValue.getSrgName();
    }

    public Field getField(final String name) {
        return this.fields.get(name);
    }

    public Collection<Field> getFields() {
        return fields.values();
    }

    public Collection<Method> getMethods() {
        return methods.values();
    }
}
