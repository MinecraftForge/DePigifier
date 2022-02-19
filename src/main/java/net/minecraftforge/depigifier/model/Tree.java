/*
 * DePigifier
 * Copyright (c) 2016-2019.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.depigifier.model;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import net.minecraftforge.depigifier.IMapper;
import net.minecraftforge.srgutils.IMappingFile;
import org.objectweb.asm.Type;

import java.util.Map;

public class Tree implements IMapper {
    public static Tree from(IMappingFile map, final boolean filterInits) {
        Tree ret = new Tree();
        map.getPackages().forEach(pkg -> ret.addPackage(pkg.getOriginal(), pkg.getMapped()));
        map.getClasses().forEach(cls -> {
            Class tcls = ret.getClass(cls.getOriginal());
            tcls.rename(cls.getMapped());
            cls.getFields().forEach(fld -> tcls.getField(fld.getOriginal()).rename(fld.getMapped()));
            cls.getMethods().stream().filter(mtd -> !filterInits || !mtd.getOriginal().startsWith("<"))
                .forEach(mtd -> tcls.getMethod(mtd.getOriginal(), mtd.getDescriptor()).rename(mtd.getMapped()));
        });
        return ret;
    }

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

    public String unmapClass(String cls) {
        Class _cls = n2oClasses.get(cls);
        if (_cls != null)
            return _cls.getOldName();
        int idx = cls.lastIndexOf('$');
        if (idx > 0)
            return unmapClass(cls.substring(0, idx)) + cls.substring(idx);

        return cls; //TODO: Packages
    }

    public String unmapField(String cls, String field) {
        Class _cls = n2oClasses.get(cls);
        return _cls == null ? field : _cls.unmapField(field);
    }

    public String unmapMethod(String cls, String method, String desc) {
        Class _cls = n2oClasses.get(cls);
        return _cls == null ? method : _cls.unmapMethod(method, mapDescriptor(desc));
    }

    public String unmapDescriptor(String desc) {
        return unmapDescriptor(Type.getArgumentTypes(desc), Type.getReturnType(desc));
    }

    public String unmapDescriptor(Type[] args, Type ret) {
        StringBuilder buf = new StringBuilder();
        buf.append('(');
        for (Type arg : args) {
            if (arg.getSort() == Type.ARRAY) {
                for (int x = 0; x < arg.getDimensions(); x++)
                    buf.append('[');
                arg = arg.getElementType();
            }
            if (arg.getSort() == Type.OBJECT)
                buf.append('L').append(unmapClass(arg.getInternalName())).append(';');
            else
                buf.append(arg.getDescriptor());
        }
        buf.append(')');

        if (ret.getSort() == Type.ARRAY) {
            for (int x = 0; x < ret.getDimensions(); x++)
                buf.append('[');
            ret = ret.getElementType();
        }
        if (ret.getSort() == Type.OBJECT)
            buf.append('L').append(unmapClass(ret.getInternalName())).append(';');
        else
            buf.append(ret.getDescriptor());

        return buf.toString();
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
