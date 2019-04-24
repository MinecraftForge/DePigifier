package net.minecraftforge.depigifier.model;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import net.minecraftforge.depigifier.IMapper;

import java.util.Map;

public class Tree implements IMapper {
    // Package map, an empty string is considered the 'default' package
    private Map<String, String> packages = new HashMap<>();
    private Map<String, Class> o2nClasses = new HashMap<>();
    private Map<String, Class> n2oClasses = new HashMap<>();
    private final Map<String, Set<Class>> references = new HashMap<>();

    public void addPackage(String oldPkg, String newPkg) {
        packages.put(oldPkg, newPkg);
    }

    public Class getClass(String oldName) {
        return o2nClasses.computeIfAbsent(oldName, k -> new Class(this, oldName));
    }

    public Class tryClass(String oldName) {
        return o2nClasses.get(oldName);
    }

    //Warning: This doesn't create a new copy, so please don't modify the returned set.
    public Collection<Class> getClasses() {
        return o2nClasses.values();
    }

    //Warning: This doesn't create a new copy, so please don't modify the returned set.
    public Set<String> getClassNames() {
        return o2nClasses.keySet();
    }

    @Override
    public String mapClass(String cls) {
        Class _cls = o2nClasses.get(cls);
        if (_cls != null)
            return _cls.getNewName();
        int idx = cls.lastIndexOf('$');
        if (idx > 0)
            return mapClass(cls.substring(0, idx)) + cls.substring(idx);

        return cls; //TODO: Packages
    }

    @Override
    public String mapField(String cls, String field) {
        Class _cls = o2nClasses.get(cls);
        return _cls == null ? field : _cls.mapField(field);
    }

    @Override
    public String mapMethod(String cls, String method, String desc) {
        Class _cls = o2nClasses.get(cls);
        return _cls == null ? method : _cls.mapMethod(method, desc);
    }

    //Map modifiers are intentionally private you should use the rename functions in the associated objects as they will update all cached lookups.
    void renameClass(Class cls, String newName) {
        //TODO: Add check to not overwrite existing?
        n2oClasses.remove(cls.getNewName());
        n2oClasses.put(newName, cls);
        references.getOrDefault(cls.getOldName(), Collections.emptySet()).forEach(c -> c.classRenamed(c, new RenamedMapper(cls.getOldName(), newName)));
    }

    //Add a notification callback when classes are renamed, this allows us to invalidate/rebuild the New-to-Old caches.
    void addReference(String cls, Class listener) {
        references.computeIfAbsent(cls, k -> new HashSet<>()).add(listener);
    }

    /*
     * Wrapper class that is the same as this tree, but renames a single class, used in renameClass, before everything updates itself.
     */
    private class RenamedMapper implements IMapper {
        private final String oldName;
        private final String newName;

        private RenamedMapper(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
        }

        @Override
        public String mapClass(String cls) {
            if (oldName.equals(cls))
                return newName;
            return Tree.this.mapClass(cls);
        }

        @Override
        public String mapField(String cls, String field) {
            return Tree.this.mapField(cls, field);
        }

        @Override
        public String mapMethod(String cls, String method, String desc) {
            return Tree.this.mapMethod(cls, method, desc);
        }
    }
}
