package net.minecraftforge.depigifier;

import joptsimple.internal.Strings;
import net.minecraftforge.depigifier.model.Class;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ClassLookup {
    private static final List<String> PRIMITIVES = Arrays.asList("void", "boolean", "char", "byte", "short", "int", "float", "long", "double");
    private static final String PRIMS = "VZCBSIFJD";
    public static String transformToSignature(final String input, final ProguardFile file) {
        return transformName(input, name -> {
            final Class aClass = file.getClass(name);
            return aClass == null ? name: aClass.getObfName();
        });
    }

    public static String transformSig(final String[] args, final String ret, final ProguardFile file) {
        final String argstring = Arrays.stream(args).map(arg -> transformToSignature(arg, file)).collect(Collectors.joining());
        return "("+argstring+")"+transformToSignature(ret, file);
    }

    public static String transformMethodNoObf(final String[] args, final String ret) {
        final String argstring = Arrays.stream(args).map(ClassLookup::transformNoObf).collect(Collectors.joining());
        return "("+argstring+")"+transformNoObf(ret);
    }

    public static String transformNoObf(final String input) {
        return transformName(input, m->m);
    }
    public static String transformName(final String input, final Function<String,String> remapper) {
        if (input.length()==0) return input;
        final int arr = input.split("\\[").length-1;
        final String array = Strings.repeat('[', arr);
        final String name = input.substring(0, input.length() - arr * 2);
        final int idx = PRIMITIVES.indexOf(name);
        if (idx > -1) {
            return array + PRIMS.charAt(idx);
        } else {
            final String obfName = remapper.apply(name);
            return array + 'L'+ obfName.replace('.','/')+';';
        }
    }
}
