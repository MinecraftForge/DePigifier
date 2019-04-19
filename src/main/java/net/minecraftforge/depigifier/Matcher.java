package net.minecraftforge.depigifier;

import com.machinezoo.noexception.Exceptions;
import net.minecraftforge.depigifier.model.Class;
import net.minecraftforge.depigifier.model.Field;
import net.minecraftforge.depigifier.model.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Matcher {

    public static final Logger LOGGER = LoggerFactory.getLogger(Matcher.class);
    private final ProguardFile oldProguard;
    private final ProguardFile newProguard;
    private final Path outputDir;
    private List<Class> existingClasses;

    public Matcher(final ProguardFile oldProguard, final ProguardFile newProguard, final Path output) {
        this.oldProguard = oldProguard;
        this.newProguard = newProguard;
        this.outputDir = output;
    }

    private static String fieldToTSRGString(Field f) {
        return f.getOwner().getProguardName().replace('.', '/') + " " + f.getProguardName();
    }

    private static String methodToTSRGString(Method m) {
        return m.getOwner().getProguardName().replace('.', '/') + " " + m.getTSRGName();
    }

    public void computeClassListDifferences() {
        final List<Class> newClasses = new ArrayList<>();
        final List<Class> missingClasses = new ArrayList<>();
        this.existingClasses = new ArrayList<>();
        differenceSet(oldProguard::getClassNames, newProguard::getClassNames, oldProguard::getClass, newProguard::getClass, (o,n)->n.setMatchedOldValue(o), ()->newClasses, ()-> existingClasses, ()-> missingClasses);
        writeFile(outputDir.resolve("newclasses.txt"), listBuilder(()->newClasses, Class::getProguardName));
        writeFile(outputDir.resolve("missingclasses.txt"), listBuilder(()->missingClasses, Class::getProguardName));
    }

    public void compareExistingClasses() {
        final List<Field> newFields = new ArrayList<>();
        final List<Method> newMethods = new ArrayList<>();
        final List<Field> missingFields = new ArrayList<>();
        final List<Method> missingMethods = new ArrayList<>();
        existingClasses.forEach(entry -> compareClass(entry, newFields, newMethods, missingFields, missingMethods));
        writeFile(outputDir.resolve("newfields.txt"), listBuilder(()->newFields, Matcher::fieldToTSRGString));
        writeFile(outputDir.resolve("missingfields.txt"), listBuilder(()->missingFields, Matcher::fieldToTSRGString));
        writeFile(outputDir.resolve("newmethods.txt"), listBuilder(()->newMethods, Matcher::methodToTSRGString));
        writeFile(outputDir.resolve("missingmethods.txt"), listBuilder(()->missingMethods, Matcher::methodToTSRGString));
        final Path tsrgOut = outputDir.resolve("oldtonew.tsrg");
        final List<String> tsrgLines = new ArrayList<>();
        existingClasses.stream().sorted(Comparator.comparing(aClass -> aClass.getMatchedOldValue().getObfName(), Comparator.comparingInt(String::length).thenComparing(String::compareTo))).forEach(nw->{
            tsrgLines.add(nw.getMatchedOldValue().getObfName().replace('.','/') + " " +nw.getObfName().replace('.','/'));
            tsrgLines.addAll(nw.getFields().stream().filter(f->Objects.nonNull(f.getMatchedOldValue())).map(f->"\t"+f.getMatchedOldValue().getObfName()+" " + f.getObfName()).sorted().collect(Collectors.toList()));
            tsrgLines.addAll(nw.getMethods().stream().filter(m->Objects.nonNull(m.getMatchedOldValue())).map(m->"\t"+m.getMatchedOldValue().getTSRGObfName(oldProguard)+" " + m.getObfName()).sorted().collect(Collectors.toList()));
        });
        Exceptions.sneak().run(()->Files.write(tsrgOut, tsrgLines, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE));
    }

    private <T> Supplier<List<String>> listBuilder(final Supplier<Collection<T>> t, final Function<T, String> stringFunction) {
        return () -> t.get().stream().map(stringFunction).sorted().collect(Collectors.toList());
    }
    private void writeFile(final Path path, final Supplier<List<String>> lines) {
        Exceptions.sneak().run(()->Files.write(path, lines.get(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE));
    }

    private <T> void differenceSet(final Supplier<Set<String>> old, final Supplier<Set<String>> nw,
                                   final Function<String,T> oldLookup, final Function<String,T> newLookup,
                                   final BiConsumer<T,T> connector,
                                   final Supplier<List<T>> newTracker, final Supplier<List<T>> existingTracker, final Supplier<List<T>> missingTracker) {
        SetDifference<String> sd = new SetDifference<>(old.get(), nw.get());
        final HashSet<String> newValues = sd.getRightOnly();
        final HashSet<String> missingValues = sd.getLeftOnly();
        final HashSet<String> matchedValues = sd.getCommon();
        final List<T> commonValues = matchedValues.stream().map(n -> new HashMap.SimpleImmutableEntry<>(oldLookup.apply(n), newLookup.apply(n))).
                peek(e -> connector.accept(e.getKey(), e.getValue())).
                map(AbstractMap.SimpleImmutableEntry::getValue).
                collect(Collectors.toList());
        missingTracker.get().addAll(missingValues.stream().map(oldLookup).collect(Collectors.toList()));
        existingTracker.get().addAll(commonValues);
        newTracker.get().addAll(newValues.stream().map(newLookup).collect(Collectors.toList()));
    }

    private void compareClass(final Class entry, final List<Field> newFieldTracked, final List<Method> newMethodTracked, final List<Field> missingFields, final List<Method> missingMethods) {
        final Class old = entry.getMatchedOldValue();
        final Class nw = entry;

        differenceSet(old::getFieldNames, nw::getFieldNames, old::getField, nw::getField, (o,n)->n.setMatchedOldValue(o),()->newFieldTracked, ArrayList::new, ()->missingFields);
        differenceSet(old::getMethodSignatures, nw::getMethodSignatures, old::getMethod, nw::getMethod, (o,n)->n.setMatchedOldValue(o),()->newMethodTracked, ArrayList::new, ()->missingMethods);
    }
}
