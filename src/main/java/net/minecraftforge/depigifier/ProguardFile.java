package net.minecraftforge.depigifier;

import com.machinezoo.noexception.Exceptions;
import net.minecraftforge.depigifier.model.Class;
import net.minecraftforge.depigifier.model.Field;
import net.minecraftforge.depigifier.model.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProguardFile {
    private static final Pattern methodParts = Pattern.compile("[\\s]{4}(?:([\\d]+):([\\d]+):|)([\\S]+)[\\s]([\\S]+)\\(([\\S]*)\\)");
    public static final Logger LOGGER = LoggerFactory.getLogger(ProguardFile.class);
    private final Path file;
    private final Map<String, Class> classes = new HashMap<>();
    private final Map<String, Class> obfClasses = new HashMap<>();

    public ProguardFile(final Path file) {
        this.file = file;
        parse();
    }

    private void parse() {
        final List<String> lines = Exceptions.sneak().get(() -> Files.readAllLines(this.file));
        Class current = null;
        for (String line: lines) {
            String[] pairs = line.split(" -> ");
            if (line.endsWith(":")) { // CLASS
                current = new Class(pairs[0],pairs[1].substring(0, pairs[1].length()-1));
                classes.put(current.getProguardName(), current);
                obfClasses.put(current.getObfName(), current);
                LOGGER.info("Class {}", current);
            } else if (line.indexOf("(") > 0) { // METHOD
                final Matcher methodparts = methodParts.matcher(pairs[0]);
                if (methodparts.matches()) {
                    final Method method = current.addMethod(new Method(methodparts.group(4), pairs[1], "("+methodparts.group(5) + ")"+methodparts.group(3),
                            Exceptions.silence().get(()->Integer.parseInt(methodparts.group(1))).orElse(0),
                            Exceptions.silence().get(()->Integer.parseInt(methodparts.group(2))).orElse(0)));
                    LOGGER.info("Method {}", method);
                } else {
                    throw new RuntimeException("Badly formatted method line: "+line);
                }
            } else {
                final String[] fieldparts = pairs[0].trim().split(" ");
                final Field field = current.addField(new Field(fieldparts[1], pairs[1], fieldparts[0]));
                LOGGER.info("Field {}", field);
            }
        }
    }

    public Set<String> getClassNames() {
        return classes.keySet();
    }

    public Class getClass(final String name) {
        return classes.get(name);
    }

    public Class getObfClass(final String obfName) {
        return obfClasses.get(obfName);
    }
}
