package net.minecraftforge.depigifier;

import com.machinezoo.noexception.Exceptions;
import net.minecraftforge.depigifier.model.Class;
import net.minecraftforge.depigifier.model.Field;
import net.minecraftforge.depigifier.model.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;

public class TSRGFile {
    public static final Logger LOGGER = LoggerFactory.getLogger(TSRGFile.class);
    private final Path file;
    private final ProguardFile proguardFile;

    public TSRGFile(final Path file, final ProguardFile proguardFile) {
        this.file = file;
        this.proguardFile = proguardFile;
        parse();
    }

    private void parse() {
        final List<String> lines = Exceptions.sneak().get(() -> Files.readAllLines(this.file));
        Class current = null;
        for (String line: lines) {
            String[] pairs = line.trim().split(" ");
            if (!line.startsWith("\t")) { // CLASS
                final String obfClassName = pairs[0].replace('/', '.');
                final String srgClassName = pairs[1].replace('/', '.');
                current = proguardFile.getObfClass(obfClassName);
                current.setSrgName(srgClassName);
                LOGGER.info("Class {} ({}) -> {}", current, obfClassName, srgClassName);
            } else if (line.indexOf("(") > 0) { // METHOD
                Method method = current.findObfMethod(pairs[0], pairs[1]);
                method.setSrgName(pairs[2]);
            } else {
            }
        }

    }
}
