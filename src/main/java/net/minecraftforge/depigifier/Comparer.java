package net.minecraftforge.depigifier;

import net.minecraftforge.depigifier.model.Class;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Comparer {

    public static final Logger LOGGER = LoggerFactory.getLogger(Comparer.class);
    private final ProguardFile oldProguard;
    private final ProguardFile newProguard;
    private Set<Class> newClasses;
    private Set<Map.Entry<Class,Class>> existingClasses;

    public Comparer(final ProguardFile oldProguard, final ProguardFile newProguard) {
        this.oldProguard = oldProguard;
        this.newProguard = newProguard;
    }

    public void computeClassListDifferences() {
        final Set<String> oldNames = oldProguard.getClassNames();
        final Set<String> newNames = newProguard.getClassNames();
        final HashSet<String> newClasses = new HashSet<>(newNames);
        newClasses.removeAll(oldNames);
        this.newClasses = newClasses.stream().map(newProguard::getClass).collect(Collectors.toSet());
        final HashSet<String> existingClasses = new HashSet<>(newNames);
        existingClasses.removeAll(newClasses);
        this.existingClasses = existingClasses.stream().map(n->new HashMap.SimpleImmutableEntry<>(newProguard.getClass(n), oldProguard.getClass(n))).collect(Collectors.toSet());
        LOGGER.info("Only in new {}", newClasses.size());
    }

    public void compareExistingClasses() {
        // set srg
        existingClasses.forEach(e->e.getKey().setSrgName(e.getValue().getSrgName()));
        existingClasses.forEach(this::compareClass);
    }

    private void compareClass(final Map.Entry<Class, Class> entry) {
        final Class old = entry.getValue();
        final Class nw = entry.getKey();

        final Set<String> oldFieldNames = old.getFieldNames();
        final Set<String> newFieldNames = nw.getFieldNames();
        final HashSet<String> newFields = new HashSet<>(newFieldNames);
        newFields.removeAll(oldFieldNames);
        LOGGER.info("New fields in {} : {}", nw, newFields);

        final Set<String> oldMethodSignatures = old.getMethodSignatures();
        final Set<String> newMethodSignatures = nw.getMethodSignatures();
        final HashSet<String> newMethods = new HashSet<>(newMethodSignatures);
        newMethods.removeAll(oldMethodSignatures);
        LOGGER.info("New methods in {} : {}", nw, newMethods);
    }
}
