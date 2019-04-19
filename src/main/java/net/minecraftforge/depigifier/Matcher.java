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
import java.util.stream.Collectors;

public class Matcher {

    public static final Logger LOGGER = LoggerFactory.getLogger(Matcher.class);
    private final ProguardFile oldProguard;
    private final ProguardFile newProguard;
    private final Path outputDir;
    private Set<Class> newClasses;
    private Set<Map.Entry<Class,Class>> existingClasses;

    public Matcher(final ProguardFile oldProguard, final ProguardFile newProguard, final Path output) {
        this.oldProguard = oldProguard;
        this.newProguard = newProguard;
        this.outputDir = output;
    }

    public void computeClassListDifferences() {
        final Set<String> oldNames = oldProguard.getClassNames();
        final Set<String> newNames = newProguard.getClassNames();
        final HashSet<String> newClasses = new HashSet<>(newNames);
        newClasses.removeAll(oldNames);
        this.newClasses = newClasses.stream().map(newProguard::getClass).collect(Collectors.toSet());
        final HashSet<String> existingClasses = new HashSet<>(newNames);
        existingClasses.removeAll(newClasses);
        this.existingClasses = existingClasses.stream().
                map(n->new HashMap.SimpleImmutableEntry<>(newProguard.getClass(n), oldProguard.getClass(n))).
                collect(Collectors.toSet());
        this.existingClasses.forEach(e->e.getKey().setMatchedOldValue(e.getValue()));
        LOGGER.info("Only in new {}", newClasses.size());
        final Path classesOut = outputDir.resolve("newclasses.txt");
        Exceptions.sneak().run(()->Files.write(classesOut, newClasses, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE));
    }

    public void compareExistingClasses() {
        List<Field> newFields = new ArrayList<>();
        List<Method> newMethods = new ArrayList<>();
        existingClasses.forEach(entry -> compareClass(entry, newFields, newMethods));
        final Path newFieldsOut = outputDir.resolve("newfields.txt");
        final List<String> newFieldList = newFields.stream().map(f->f.getOwner().getProguardName().replace('.','/')+" "+f.getProguardName()).collect(Collectors.toList());
        Exceptions.sneak().run(()->Files.write(newFieldsOut, newFieldList, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE));
        final Path newMethodsOut = outputDir.resolve("newmethods.txt");
        final List<String> newMethodList = newMethods.stream().map(m->m.getOwner().getProguardName().replace('.','/')+" "+m.getTSRGName()).collect(Collectors.toList());
        Exceptions.sneak().run(()->Files.write(newMethodsOut, newMethodList, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE));
        final Path tsrgOut = outputDir.resolve("oldtonew.tsrg");
        final List<String> tsrgLines = new ArrayList<>();
        existingClasses.stream().map(Map.Entry::getKey).sorted(Comparator.comparing(aClass -> aClass.getMatchedOldValue().getObfName(), Comparator.comparingInt(String::length).thenComparing(String::compareTo))).forEach(nw->{
            tsrgLines.add(nw.getMatchedOldValue().getObfName().replace('.','/') + " " +nw.getObfName().replace('.','/'));
            tsrgLines.addAll(nw.getFields().stream().filter(f->Objects.nonNull(f.getMatchedOldValue())).map(f->"\t"+f.getMatchedOldValue().getObfName()+" " + f.getObfName()).sorted().collect(Collectors.toList()));
            tsrgLines.addAll(nw.getMethods().stream().filter(m->Objects.nonNull(m.getMatchedOldValue())).map(m->"\t"+m.getMatchedOldValue().getTSRGObfName(oldProguard)+" " + m.getObfName()).sorted().collect(Collectors.toList()));
        });
        Exceptions.sneak().run(()->Files.write(tsrgOut, tsrgLines, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE));
    }

    private void compareClass(final Map.Entry<Class, Class> entry, final List<Field> newFieldTracked, final List<Method> newMethodTracked) {
        final Class old = entry.getValue();
        final Class nw = entry.getKey();

        final Set<String> oldFieldNames = old.getFieldNames();
        final Set<String> newFieldNames = nw.getFieldNames();
        final HashSet<String> newFields = new HashSet<>(newFieldNames);
        newFields.removeAll(oldFieldNames);
        LOGGER.info("New fields in {} : {}", nw, newFields);
        newFieldTracked.addAll(newFields.stream().map(nw::getField).collect(Collectors.toList()));
        final HashSet<String> existingFields = new HashSet<>(newFieldNames);
        existingFields.removeAll(newFields);
        final Set<Field> fiels = existingFields.stream().map(n -> new HashMap.SimpleImmutableEntry<>(nw.getField(n), old.getField(n))).
                peek(e -> e.getKey().setMatchedOldValue(e.getValue())).
                map(AbstractMap.SimpleImmutableEntry::getKey).
                collect(Collectors.toSet());
        LOGGER.info("Existing fields in {} :\n\t{}", nw, fiels.stream().map(Object::toString).collect(Collectors.joining("\n\t")));

        final Set<String> oldMethodSignatures = old.getMethodSignatures();
        final Set<String> newMethodSignatures = nw.getMethodSignatures();
        final HashSet<String> newMethods = new HashSet<>(newMethodSignatures);
        newMethods.removeAll(oldMethodSignatures);
        LOGGER.info("New methods in {} : {}", nw, newMethods);
        newMethodTracked.addAll(newMethods.stream().map(nw::getMethod).collect(Collectors.toList()));
        final HashSet<String> existingMethods = new HashSet<>(newMethodSignatures);
        existingMethods.removeAll(newMethods);
        // copy srg names from old to new for existing
        final Set<Method> meths = existingMethods.stream().map(n -> new HashMap.SimpleImmutableEntry<>(nw.getMethod(n), old.getMethod(n))).
                peek(e -> e.getKey().setMatchedOldValue(e.getValue())).
                map(AbstractMap.SimpleImmutableEntry::getKey).
                collect(Collectors.toSet());
        LOGGER.info("Existing methods in {} :\n\t{}", nw, meths.stream().map(Object::toString).collect(Collectors.joining("\n\t")));
    }
}
