package net.minecraftforge.depigifier;

import joptsimple.internal.Strings;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ClassLookup {
    private static final List<String> PRIMITIVES = Arrays.asList("void", "boolean", "char", "byte", "short", "int", "float", "long", "double");
    private static final String PRIMS = "VZCBSIFJD";

    public static String transformMethodNoObf(final String[] args, final String ret) {
        final String argstring = Arrays.stream(args).map(ClassLookup::transformSignature).collect(Collectors.joining());
        return "("+argstring+")"+transformSignature(ret);
    }

    public static String transformNoObf(final String input) {
        return transformName(input, m->m);
    }

    public static String transformName(final String input, final Function<String,String> remapper) {
        return remapper.apply(input);
    }

    // Transform proguard 'source'-esq classes to internal format
    // java.lang.String[][] -> [[Ljava/lang/String;
    // void -> V
    public static String transformSignature(final String input) {
        if (input.length()==0) return input;
        final int arr = input.split("\\[").length-1;
        final String array = Strings.repeat('[', arr);
        final String name = input.substring(0, input.length() - arr * 2);
        final int idx = PRIMITIVES.indexOf(name);
        if (idx > -1) {
            return array + PRIMS.charAt(idx);
        } else {
            return array + 'L' + name.replace('.', '/') + ';';
        }
    }

    public static String cleanInternalName(final String name) {
        if (name.endsWith(";")) {
            return name.substring(name.indexOf('L'), name.length()-1);
        } else {
            return name;
        }
    }
}
