package net.minecraftforge.depigifier.model;

public class Method {
    private final String proguardName;
    private final String obfName;
    private final String signature;
    private final int lineLow;
    private final int lineHigh;

    public Method(final String proguardName, final String obfName, final String signature, final int lineLow, final int lineHigh) {
        this.proguardName = proguardName;
        this.obfName = obfName;
        this.signature = signature;
        this.lineLow = lineLow;
        this.lineHigh = lineHigh;
    }

    @Override
    public String toString() {
        return proguardName+" "+signature + " ("+this.lineLow +"->"+this.lineHigh+")";
    }

    public String getMethodSignature() {
        return proguardName + signature;
    }
}
