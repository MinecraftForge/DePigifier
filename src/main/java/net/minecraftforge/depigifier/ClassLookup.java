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
            if (name.endsWith(";")) {
                int l = name.indexOf('L');
                final Class aClass = file.getClass(name.substring(l+1, name.length()-1));
                return aClass == null ? name: Strings.repeat('[', l)+"L"+aClass.getObfName()+";";
            }
            return name;
        });
    }

    public static String transformSig(final String[] args, final String ret, final ProguardFile file) {
        final String argstring = Arrays.stream(args).map(arg -> transformToSignature(arg, file)).collect(Collectors.joining());
        return "("+argstring+")"+transformToSignature(ret, file);
    }

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

    public static String transformSignature(final String input) {
        // transforms from . to / and int to I
        if (input.length()==0) return input;
        final int arr = input.split("\\[").length-1;
        final String array = Strings.repeat('[', arr);
        final String name = input.substring(0, input.length() - arr * 2);
        final int idx = PRIMITIVES.indexOf(name);
        if (idx > -1) {
            return array + PRIMS.charAt(idx);
        } else {
            return array + 'L' + transformInternalName(name) + ';';
        }
    }

    public static String transformInternalName(final String input) {
        return input.replace('.', '/');
    }

    public static String cleanInternalName(final String name) {
        if (name.endsWith(";")) {
            return name.substring(name.indexOf('L'), name.length()-1);
        } else {
            return name;
        }
    }
}
