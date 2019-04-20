package net.minecraftforge.depigifier.model;

import net.minecraftforge.depigifier.ClassLookup;
import net.minecraftforge.depigifier.ProguardFile;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Method {
    private final String proguardName;
    private final String obfName;
    private final String[] args;
    private final String returnType;
    private final int lineLow;
    private final int lineHigh;
    private String srgName;
    private Method matchedOldValue;
    private Class owner;

    public Method(final String proguardName, final String obfName, final String args, final String returnType, final int lineLow, final int lineHigh) {
        this.proguardName = proguardName;
        this.obfName = obfName;
        this.args = Arrays.stream(args.split(",")).map(ClassLookup::transformSignature).toArray(String[]::new);
        this.returnType = ClassLookup.transformSignature(returnType);
        this.lineLow = lineLow;
        this.lineHigh = lineHigh;
    }

    @Override
    public String toString() {
        return getOwner().getProguardName() + " " + getMethodSignature()+ " ("+this.lineLow +"->"+this.lineHigh+") -> " + this.srgName;
    }

    public String getMethodSignature() {
        return proguardName + "("+String.join(",",this.args)+")"+this.returnType;
    }

    public String getObfSignature(final ProguardFile proguardFile) {
        return obfName+ClassLookup.transformSig(args, returnType, proguardFile);
    }

    public void setSrgName(final String srgName) {
        this.srgName = srgName;
    }

    public String getSrgName() {
        return srgName;
    }

    public Method getMatchedOldValue() {
        return matchedOldValue;
    }

    public void setMatchedOldValue(final Method matchedOldValue) {
        this.matchedOldValue = matchedOldValue;
        this.srgName = matchedOldValue.getSrgName();
    }

    public Class getOwner() {
        return owner;
    }

    public void setOwner(final Class owner) {
        this.owner = owner;
    }

    public String getProguardName() {
        return proguardName;
    }

    public String getTSRGName() {
        return proguardName+" "+ClassLookup.transformMethodNoObf(args, returnType);
    }

    public String getObfName() {
        return obfName;
    }

    public String getTSRGObfName(final ProguardFile pgFile) {
        return obfName+" "+ClassLookup.transformSig(args, returnType, pgFile);
    }
}
