package net.minecraftforge.depigifier;

import com.machinezoo.noexception.Exceptions;
import joptsimple.*;
import joptsimple.util.PathConverter;
import joptsimple.util.PathProperties;
import net.minecraftforge.depigifier.model.Tree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Unpig {
    public static void main(String... args) {
        final OptionParser optionParser = new OptionParser();
        /*TODO: I don't really care about old SRG -> new right now.
         * I already have scripts to convert this using a old->new map
         * So readd this later to make those scripts not needed
         *
        final ArgumentAcceptingOptionSpec<Path> inSrgFile = optionParser.accepts("srg", "Old SRG file").
                withRequiredArg().
                withValuesConvertedBy(new PathConverter(PathProperties.FILE_EXISTING, PathProperties.READABLE));
        */
        final ArgumentAcceptingOptionSpec<Path> manualMapFile = optionParser.accepts("mapping", "Mapping file containing manual matches").
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
                withValuesConvertedBy(new PathConverter()).
                defaultsTo(Paths.get("output"));

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
        //final Path srgFile = argset.valueOf(inSrgFile);
        final Path output = argset.valueOf(outDir);
        final Path manualMap = argset.valueOf(manualMapFile);

        final Tree oldTree = MappingFile.load(oldPG, true);
        final Tree newTree = MappingFile.load(newPG, true);
        /*
        if (argset.has(inSrgFile)) {
            final TSRGFile tsrgFile = new TSRGFile(srgFile, oldProguard);
        }
        */

        if (!Files.exists(output)) {
            try {
                Files.createDirectories(output);
            } catch (IOException e) {
                System.out.println("Failed to create output directory: " + output);
                e.printStackTrace();
                System.exit(1);
            }
        }

        Matcher comp = new Matcher(oldTree, newTree, output);

        if (argset.has(manualMapFile)) {
            final Tree manualMappings = MappingFile.load(manualMap, false);
            comp.addMapper(manualMappings);
        }
        comp.computeClassListDifferences();
        comp.compareExistingClasses();
    }
}
