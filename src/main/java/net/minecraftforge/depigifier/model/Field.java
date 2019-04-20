package net.minecraftforge.depigifier.model;

import jdk.nashorn.internal.objects.AccessorPropertyDescriptor;
import net.minecraftforge.depigifier.ClassLookup;

public class Field {
    private final String proguardName;
    private final String obfName;
    private final String signature;
    private String srgName;
    private Field matchedOldValue;
    private Class owner;

    public Field(final String proguardName, final String obfName, final String signature) {
        this.proguardName = proguardName;
        this.obfName = obfName;
        this.signature = ClassLookup.transformSignature(signature);
    }

    @Override
    public String toString() {
        return ""+signature+" "+ owner.getProguardName()+" "+ proguardName;
    }

    public String getProguardName() {
        return proguardName;
    }

    public Field getMatchedOldValue() {
        return matchedOldValue;
    }

    public void setMatchedOldValue(final Field matchedOldValue) {
        this.matchedOldValue = matchedOldValue;
        this.srgName = matchedOldValue.getSrgName();
    }

    public String getSrgName() {
        return srgName;
    }

    public void setOwner(final Class owner) {
        this.owner = owner;
    }

    public Class getOwner() {
        return owner;
    }

    public String getObfName() {
        return obfName;
    }
}
