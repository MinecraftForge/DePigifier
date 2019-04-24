package net.minecraftforge.depigifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.machinezoo.noexception.Exceptions;

import net.minecraftforge.depigifier.model.Tree;
import net.minecraftforge.depigifier.model.Class;
import net.minecraftforge.depigifier.model.Method;

public class MappingFile {
    public static Tree load(final Path file) {
        final List<String> lines = Exceptions.sneak().get(() -> Files.readAllLines(file)).
            stream().map(l -> { //Trim comments and trailing whitespace
                int idx = l.indexOf('#');
                if (idx == -1) return l;
                while (idx > 0 && Character.isWhitespace(l.charAt(idx)))
                    idx--;
                return l.substring(0, idx - 1);
            })
            .filter(l -> !l.isEmpty())
            .collect(Collectors.toList());

        if (lines.isEmpty())
            throw new IllegalArgumentException("Tried to load mapping file with no entries");

        Tree tree = new Tree();
        String first = lines.get(0);
        if (first.length() > 3 && Arrays.asList("PK:", "CL:", "FD:", "MD:").contains(first.substring(0, 3))) {
            /* SRG Format:
             * PK: old/package new/package
             * CL: old/package/Class new/package/Class
             * FD: old/package/Class/field new/package/Class/field
             * MD: old/package/Class/method ()Z new/package/Class/method ()Z
             *
             * "." is considered default package in PK lines.
             */
            for (String line : lines) {
                String[] pts = line.split(" ");
                if ("PK:".equals(pts[0]))
                    tree.addPackage(pts[1].equals(".") ? "" : pts[1], pts[2].equals(".") ? "" : pts[2]);
                else if ("CL:".equals(pts[0]))
                    tree.getClass(pts[1]).rename(pts[2]);
                else if ("FD:".equals(pts[0]))
                    tree.getClass(getClass(pts[1])).getField(getName(pts[1])).rename(getName(pts[2]));
                else if ("MD:".equals(pts[0]))
                    tree.getClass(getClass(pts[1])).getMethod(getName(pts[1]), pts[2]).rename(getName(pts[3]));
            }
        } else if (first.contains(" -> ")) {
            /* Proguard Format:
             *
             * old.package.Class -> new.package.Class:
             *     OldFieldType oldFieldName -> newName
             *     LineStart:LineEnd:returnType oldMethodName() -> newName
             *
             * Example:
             * bizz.Foo -> buzz.Bar:
             *     long someLong -> a
             *     bizz.Foo myself -> b
             *     10:20:void someFunction(long,java.lang.String) -> a
             *
             * No Package support
             */
            Class current = null;
            for (String line : lines) {
                String[] pts = line.trim().split(" ");
                if (!line.startsWith("    ")) {
                    current = tree.getClass(pts[0].replace('.', '/'));
                    current.rename(pts[2].substring(0, pts[2].length() - 1).replace('.', '/')); //Trim :
                } else if (!line.contains("(")) {
                    if (current == null)
                        throw new IllegalArgumentException("Tried to load an invalid Proguard file, missing current class: " + line);
                    current.getField(pts[1]).setType(ClassLookup.transformSignature(pts[0])).rename(pts[3]);
                } else {
                    if (current == null)
                        throw new IllegalArgumentException("Tried to load an invalid Proguard file, missing current class: " + line);

                    int start = -1;
                    int end = -1;

                    if (pts[0].indexOf(':') != -1) { //Line nuumbers *should* always exist, but possibly not in old PG versions.
                        String[] s = pts[0].split(":");
                        start = Exceptions.silence().get(() -> Integer.parseInt(s[0])).orElse(-1);
                        end   = Exceptions.silence().get(() -> Integer.parseInt(s[1])).orElse(-1);
                        pts[0] = s[2];
                    }

                    int idx = pts[1].indexOf("(");
                    String name = pts[1].substring(0, idx);
                    String desc = "(" + Arrays.stream(pts[1].substring(idx + 1, pts[1].length() - 1).split(","))
                            .map(ClassLookup::transformSignature).collect(Collectors.joining())
                            + ")" + ClassLookup.transformSignature(pts[0]);
                    Method mtd = current.getMethod(name, desc);
                    if (start != -1 && end != -1)
                        mtd.setLines(start, end);
                    mtd.rename(pts[3]);
                }
            }
        } else {
            Class current = null;

            for (String line : lines) {
                if (line.charAt(0) == '\t') {
                    /* TSRG Format:
                     * old/package/ new/package/
                     * old/package/Class new/package/Class
                     * \toldName newName
                     * \toldName oldDesc newName
                     *
                     * Package lines must have BOTH parts ending in /, "/" is considered default package
                     * Order matters, any line which starts with \t is assumed to be a field or method for the last 'class' line encountered.
                     * Descriptors on fields are not supported
                     */
                    if (current == null)
                        throw new IllegalArgumentException("Tried to load an invalid TSRG file, missing current class: " + line);
                    String[] pts = line.substring(1).split(" ");
                    if (pts.length == 2)
                        current.getField(pts[0]).rename(pts[1]);
                    else if (pts.length == 3)
                        current.getMethod(pts[0], pts[1]).rename(pts[2]);
                } else {
                    /* CSRG Format:
                     * old/package/ new/package/
                     * old/package/Class new/package/Class
                     * old/package/Class oldField newName
                     * old/package/Class oldMethod oldDesc newName
                     *
                     * Package lines must have BOTH parts ending in /, "/" is considered default package
                     * Descriptors on fields are not supported
                     */
                    String[] pts = line.substring(1).split(" ");
                    if (pts.length == 2 && pts[0].endsWith("/") && pts[1].endsWith("/"))
                        tree.addPackage(pts[0].substring(0, pts[0].length() - 1), pts[1].substring(0, pts[1].length() - 1));
                    else if (pts.length == 2)
                        current = tree.getClass(pts[0]).rename(pts[1]);
                    else if (pts.length == 3)
                        tree.getClass(pts[0]).getField(pts[1]).rename(pts[2]);
                    else if (pts.length == 4)
                        tree.getClass(pts[0]).getMethod(pts[1], pts[2]).rename(pts[3]);
                }


            }
        }
        return tree;
    }

    private static String getName(String fullPath) {
        int idx = fullPath.lastIndexOf('/');
        return idx == -1 ? fullPath : fullPath.substring(idx);
    }
    private static String getClass(String fullPath) {
        int idx = fullPath.lastIndexOf('/');
        return idx == -1 ? fullPath : fullPath.substring(0, idx);
    }

}
