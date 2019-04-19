package net.minecraftforge.depigifier.model;

public class Field {
    private final String proguardName;
    private final String obfName;
    private final String signature;

    public Field(final String proguardName, final String obfName, final String signature) {
        this.proguardName = proguardName;
        this.obfName = obfName;
        this.signature = signature;
    }

    @Override
    public String toString() {
        return proguardName + "{"+signature+"}";
    }

    public String getProguardName() {
        return proguardName;
    }
}
