package net.minecraftforge.depigifier;

import com.machinezoo.noexception.Exceptions;
import joptsimple.*;
import joptsimple.util.PathConverter;
import joptsimple.util.PathProperties;

import java.nio.file.Path;

public class Unpig {
    public static void main(String... args) {
        final OptionParser optionParser = new OptionParser();
        final ArgumentAcceptingOptionSpec<Path> inSrgFile = optionParser.accepts("srg", "Old SRG file").
                withRequiredArg().
                withValuesConvertedBy(new PathConverter(PathProperties.FILE_EXISTING, PathProperties.READABLE));
        final ArgumentAcceptingOptionSpec<Path> tSrgMapFile = optionParser.accepts("mapping", "TSRG formatted mapping file").
                withRequiredArg().
                withValuesConvertedBy(new PathConverter(PathProperties.FILE_EXISTING, PathProperties.READABLE));
        final ArgumentAcceptingOptionSpec<Path> oldPGFile = optionParser.accepts("oldPG", "Old ProGuard file").
                withRequiredArg().
                withValuesConvertedBy(new PathConverter(PathProperties.FILE_EXISTING, PathProperties.READABLE)).
                required();
        final ArgumentAcceptingOptionSpec<Path> newPGFile = optionParser.accepts("newPG", "New ProGuard file").
                withRequiredArg().
                withValuesConvertedBy(new PathConverter(PathProperties.FILE_EXISTING, PathProperties.READABLE)).
                required();
        final ArgumentAcceptingOptionSpec<Path> outDir = optionParser.accepts("out", "Directory to output to").
                withRequiredArg().
                withValuesConvertedBy(new PathConverter(PathProperties.DIRECTORY_EXISTING)).
                required();

        final AbstractOptionSpec<Void> forHelp = optionParser.accepts("help", "Help").forHelp();

        OptionSet argset = null;
        try {
            argset = optionParser.parse(args);
        } catch (OptionException e) {
            System.out.println(e.getMessage());
            Exceptions.sneak().run(()->optionParser.printHelpOn(System.out));
            System.exit(1);
        }
        if (argset.has(forHelp)) {
            Exceptions.sneak().run(()->optionParser.printHelpOn(System.out));
            System.exit(1);
        }


        final Path oldPG = argset.valueOf(oldPGFile);
        final Path newPG = argset.valueOf(newPGFile);
        final Path srgFile = argset.valueOf(inSrgFile);
        final Path output = argset.valueOf(outDir);
        final Path tsrgMapper = argset.valueOf(tSrgMapFile);
        final ProguardFile oldProguard = new ProguardFile(oldPG);
        final ProguardFile newProguard = new ProguardFile(newPG);
        if (argset.has(inSrgFile)) {
            final TSRGFile tsrgFile = new TSRGFile(srgFile, oldProguard);
        }
        Matcher comp = new Matcher(oldProguard, newProguard, output);

        if (argset.has(tSrgMapFile)) {
            final TSRGSubstitutions tsrgSubstitutions = new TSRGSubstitutions(tsrgMapper);
            comp.addMapper(tsrgSubstitutions);
        }
        comp.computeClassListDifferences();
        comp.compareExistingClasses();
    }
}
