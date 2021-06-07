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

package net.minecraftforge.depigifier;

import com.machinezoo.noexception.Exceptions;
import net.minecraftforge.depigifier.model.Class;
import net.minecraftforge.depigifier.model.Field;
import net.minecraftforge.depigifier.model.Method;
import net.minecraftforge.depigifier.model.Tree;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Matcher {

    public static final Logger LOGGER = LoggerFactory.getLogger(Matcher.class);
    private final Tree oldTree;
    private final Tree oldTreeReversed;
    private final Tree newTree;
    private final Tree newTreeReversed;
    private final Path outputDir;
    private final List<IMapper> mappers = new ArrayList<>();
    private List<Class> existingClasses;

    private Map<Class, Class> forcedClasses = new HashMap<>();
    private Map<Field, Field> forcedFields = new HashMap<>();
    private Map<Method, Method> forcedMethods = new HashMap<>();

    private List<Class> newClasses = new ArrayList<>();
    private List<Field> newFields = new ArrayList<>();
    private List<Method> newMethods = new ArrayList<>();

    private List<Class> missingClasses = new ArrayList<>();
    private List<Field> missingFields = new ArrayList<>();
    private List<Method> missingMethods = new ArrayList<>();

    public Matcher(final Tree oldTree, final Tree oldTreeReversed, final Tree newTree, final Tree newTreeReversed, final Path output) {
        this.oldTree = oldTree;
        this.oldTreeReversed = oldTreeReversed;
        this.newTree = newTree;
        this.newTreeReversed = newTreeReversed;
        this.outputDir = output;
    }

    private static String fieldToTSRGString(Field f) {
        return f.getOwner().getOldName() + " " + f.getOldName();
    }

    private static String methodToTSRGString(Method m) {
        return m.getOwner().getOldName() + " " + m.getOldName() + " " + m.getOldDesc();
    }

    public void computeClassListDifferences() {
        this.existingClasses = new ArrayList<>();
        differenceSet(oldTree::getClassNames, newTree::getClassNames, this::mapClass, oldTree::tryClass, newTree::tryClass, forcedClasses::put, ()->newClasses, ()-> existingClasses, ()-> missingClasses);
        writeFile(outputDir.resolve("newclasses.txt"), listBuilder(()->newClasses, Class::getOldName));
        writeFile(outputDir.resolve("missingclasses.txt"), listBuilder(()->missingClasses, Class::getOldName));
        System.out.println("missing/new/total");
        System.out.println("Classes: " + missingClasses.size() + "/" + newClasses.size() + "/" + newTree.getClasses().size());
    }

    public void compareExistingClasses(Path oldJar, Path newJar) {
        existingClasses.forEach(aClass -> compareClass(aClass, newFields, newMethods, missingFields, missingMethods, oldJar, newJar));
        writeFile(outputDir.resolve("newfields.txt"), listBuilder(()->newFields, Matcher::fieldToTSRGString));
        writeFile(outputDir.resolve("missingfields.txt"), listBuilder(()->missingFields, Matcher::fieldToTSRGString));
        writeFile(outputDir.resolve("newmethods.txt"), listBuilder(()->newMethods, Matcher::methodToTSRGString));
        writeFile(outputDir.resolve("missingmethods.txt"), listBuilder(()->missingMethods, Matcher::methodToTSRGString));
        final Path tsrgOut = outputDir.resolve("oldtonew.tsrg");
        final List<String> tsrgLines = new ArrayList<>();
        existingClasses.stream().sorted(
                Comparator.comparing(aClass -> forcedClasses.get(aClass).getNewName(),
                        Comparator.comparingInt(String::length).thenComparing(String::compareTo))).
                forEach(cl -> buildTSRG(cl, tsrgLines));
        Exceptions.sneak().run(()->Files.write(tsrgOut, tsrgLines, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE));
        System.out.println("Fields : " + missingFields.size() + "/" + newFields.size() + "/" + newTree.getClasses().stream().mapToInt(c -> c.getFields().size()).sum());
        System.out.println("Methods: " + missingMethods.size() + "/" + newMethods.size() + "/" + newTree.getClasses().stream().mapToInt(c -> c.getMethods().size()).sum());

        dumpMagiDots();

        missingClasses.stream().sorted((c1,c2) -> c1.getOldName().compareTo(c2.getOldName())).forEach(m -> {
            String name = m.getOldName().substring(m.getOldName().lastIndexOf('/')+1);
            if (!name.equals("package-info")) {
                List<String> lst = newClasses.stream().map(Class::getOldName).filter(n -> n.endsWith(name)).collect(Collectors.toList());
                if (lst.size() == 1)
                    System.out.println(m.getOldName() + " " + lst.get(0));
                else if (!lst.isEmpty()) {
                    System.out.println(m.getOldName());
                    lst.forEach(l -> System.out.println("  " + l));
                }
            }
        });
    }

    private void dumpMagiDots() {
        Function<Field, String> fts = f -> f.getOwner().getNewName() + "." + f.getNewName();
        BiFunction<Method, Tree, String> mts = (m, t) -> m.getOwner().getNewName() + "." + m.getNewName() + " " + m.getNewDesc(t);

        writeFile(outputDir.resolve("joined_forced.txt"), () -> {
            List<String> clss = forcedClasses.keySet().stream().map(nw -> forcedClasses.get(nw).getNewName() + " " + nw.getNewName()).collect(Collectors.toList());
            Collections.sort(clss, (o1, o2) -> Sorters.CLASSES.compare(o1.split(" ")[0], o2.split(" ")[0]));
            clss.add(0, "[CLASSES]");
            List<String> flds = forcedFields.keySet().stream().map(nw -> fts.apply(forcedFields.get(nw)) + " " + fts.apply(nw) ).collect(Collectors.toList());
            Collections.sort(flds, (o1, o2) -> Sorters.FIELDS.compare(o1.split(" ")[0], o2.split(" ")[0]));
            flds.add(0, "[FIELDS]");
            List<String> mtds = forcedMethods.keySet().stream().map(nw -> mts.apply(forcedMethods.get(nw), oldTree) + " " + mts.apply(nw, newTree)).collect(Collectors.toList());
            Collections.sort(mtds, (o1, o2) -> Sorters.METHODS.compare(o1.split(" ")[0] + " " + o1.split(" ")[1], o2.split(" ")[0] + " " + o2.split(" ")[1]));
            mtds.add(0, "[METHODS]");

            //Merge them all
            clss.addAll(flds);
            clss.addAll(mtds);
            return clss;
        });
    }

    private void buildTSRG(final Class nw, final List<String> tsrgLines) {
        tsrgLines.add(forcedClasses.get(nw).getNewName() + " " +nw.getNewName());
        tsrgLines.addAll(nw.getFields().stream().filter(f-> Objects.nonNull(forcedFields.get(f))).map(f->"\t"+forcedFields.get(f).getNewName()+" " + f.getNewName()).sorted().collect(Collectors.toList()));
        tsrgLines.addAll(nw.getMethods().stream().filter(m->Objects.nonNull(forcedMethods.get(m))).map(m->"\t"+forcedMethods.get(m).getNewName() + " " + forcedMethods.get(m).getNewDesc(oldTree)+" " + m.getNewName()).sorted().collect(Collectors.toList()));
    }

    private <T> Supplier<List<String>> listBuilder(final Supplier<Collection<T>> t, final Function<T, String> stringFunction) {
        return () -> t.get().stream().map(stringFunction).sorted().collect(Collectors.toList());
    }
    private void writeFile(final Path path, final Supplier<List<String>> lines) {
        Exceptions.sneak().run(()->Files.write(path, lines.get(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE));
    }

    private <T> void differenceSet(final Supplier<Set<String>> old, final Supplier<Set<String>> nw,
                                   final Function<String,String> oldNameTransformer,
                                   final Function<String,T> oldLookup, final Function<String,T> newLookup,
                                   final BiConsumer<T,T> connector,
                                   final Supplier<List<T>> newTracker, final Supplier<List<T>> existingTracker, final Supplier<List<T>> missingTracker) {
        final Map<String, String> transformedOldNames = old.get().stream().map(o -> new HashMap.SimpleImmutableEntry<>(oldNameTransformer.apply(o), o)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        SetDifference<String> sd = new SetDifference<>(transformedOldNames.keySet(), nw.get());
        final HashSet<String> newValues = sd.getRightOnly();
        final HashSet<String> missingValues = sd.getLeftOnly();
        final HashSet<String> matchedValues = sd.getCommon();
        final List<T> commonValues = matchedValues.stream().map(n -> new HashMap.SimpleImmutableEntry<>(oldLookup.apply(transformedOldNames.get(n)), newLookup.apply(n))).
                peek(e -> connector.accept(e.getValue(), e.getKey())).
                map(AbstractMap.SimpleImmutableEntry::getValue).
                collect(Collectors.toList());
        missingTracker.get().addAll(missingValues.stream().filter(n -> !n.startsWith("&")).map(n -> oldLookup.apply(transformedOldNames.get(n))).collect(Collectors.toList()));
        existingTracker.get().addAll(commonValues);
        newTracker.get().addAll(newValues.stream().map(newLookup).collect(Collectors.toList()));
    }

    private void compareClass(final Class entry, final List<Field> newFieldTracked, final List<Method> newMethodTracked, final List<Field> missingFields, final List<Method> missingMethods, Path oldJar, Path newJar) {
        final Class old = forcedClasses.get(entry);
        final Class nw = entry;
        BiFunction<Class, String, Method> tryMethod = (cls, sig) -> {
            int idx = sig.indexOf('(');
            return cls.tryMethod(sig.substring(0, idx), sig.substring(idx));
        };
        
        Function<String, String> mtdMapper = (sig -> mapMethod(old.getOldName(), sig));

        Map<String, String> lambdaMappings = new HashMap<>();
        List<Method> lambdasOld = getLambdas(old);
        List<Method> lambdasNew = getLambdas(nw);
		if(!lambdasOld.isEmpty() && !lambdasNew.isEmpty()) {
			computeLambdas(lambdasOld, lambdasNew, lambdaMappings, oldJar, newJar);

			// A lambda from the old jar gets mapped to a new lambda in the new jar.
			// If this new lambda already exists in the old jar, then the already existing
			// one also need to be mapped to avoid a crash.
			// e.g. we find a mapping lambda$null$1 -> lambda$null$5, but a lambda
			// "lambda$null$5" is also present in
			// the old jar. So we add a mapping lambda$null$5 -> &lambda$null$5 to prevent a
			// crash in differenceSet.
			Map<String, String> collisionResolution = new HashMap<>();
			for(Entry<String, String> mapping : lambdaMappings.entrySet()) {
				if(mapping.getKey().equals(mapping.getValue())) {
					continue;
				}

				String newName = mapping.getValue();
				while(old.getMethodSignatures().contains(newName)) {
					if(lambdaMappings.containsKey(newName)) {
						newName = lambdaMappings.get(newName);
						if(newName.equals(mapping.getValue())) {
							//found a circle
							break;
						}
					}else {
						collisionResolution.put(mapping.getValue(), "&" + mapping.getValue());
						break;
					}
				}
			}
			lambdaMappings.putAll(collisionResolution);

			mtdMapper = oldSig -> {
				String mappedSig = mapMethod(old.getOldName(), oldSig);
				if(!mappedSig.equals(oldSig))
					return mappedSig;
				return lambdaMappings.getOrDefault(oldSig, oldSig);
			};
		}

        differenceSet(old::getFieldNames, nw::getFieldNames, fld -> mapField(old.getOldName(), fld), old::tryField, nw::tryField, forcedFields::put,()->newFieldTracked, ArrayList::new, ()->missingFields);
        differenceSet(old::getMethodSignatures, nw::getMethodSignatures, mtdMapper, sig -> tryMethod.apply(old, sig), sig -> tryMethod.apply(nw, sig), forcedMethods::put,()->newMethodTracked, ArrayList::new, ()->missingMethods);

    }
    
    private void computeLambdas(List<Method> oldLambdas, List<Method> newLambdas, Map<String, String> matcher, Path oldJar, Path newJar) {
        if(oldLambdas.isEmpty() || newLambdas.isEmpty()) {
        	return;
        }
    	
        List<InsnList> oldLambdasInsn = getInstructions(oldJar, oldLambdas, oldTree);
        List<InsnList> newLambdasInsn = getInstructions(newJar, newLambdas, newTree);
        
        for(int i = 0; i < oldLambdas.size(); i++) {
        	if(oldLambdasInsn.get(i) == null) {
        		continue;
        	}
        	
        	String desc = oldLambdas.get(i).getNewDesc(newTree);
        	Type retType = Type.getReturnType(desc);
        	
        	List<Map.Entry<Integer, Double>> matches = new ArrayList<>();
        	for(int j = 0; j < newLambdas.size(); j++) {
        		if(newLambdasInsn.get(j) == null) {
        			continue;
        		}
        		
        		String newDesc = newLambdas.get(j).getNewDesc(newTree);
        		if(!Type.getReturnType(newDesc).equals(retType)) {
        			continue; //different return type
        		}
        		
        		double distance = EditDistance.computeDistance(oldLambdasInsn.get(i), newLambdasInsn.get(j), oldTreeReversed, newTreeReversed);
        		matches.add(new HashMap.SimpleImmutableEntry<>(j, distance));
        	}
        	
        	if(!matches.isEmpty()) {
        		matches.sort(Comparator.comparingDouble(Map.Entry::getValue));
        		
        		boolean perfectMatch = matches.get(0).getValue() == 0.0 && (matches.size() == 1 || matches.get(1).getValue() > 0.0);
        		boolean similarEnough = matches.get(0).getValue() <= 0.15 && (matches.size() == 1 || matches.get(1).getValue() > 0.2);
        		if(perfectMatch || similarEnough) {         		
            		Method matchingLambda = newLambdas.get(matches.get(0).getKey());
            		
            		String newSig = matchingLambda.getOldName() + matchingLambda.getOldDesc();
            		if(matcher.containsValue(newSig))
                        continue;
                    matcher.put(oldLambdas.get(i).getOldName() + oldLambdas.get(i).getOldDesc(), newSig);
        		}
        	}
        	
        }
        
        return;
    }

    
    private List<Method> getLambdas(Class clazz) {
        return clazz.getMethods()
                .stream()
                .filter(mtd -> mtd.getOldName().contains("lambda$"))
                .collect(Collectors.toList());
    }
    
    private List<InsnList> getInstructions(Path jar, List<Method> lambdas, IMapper mapper) {
    	List<InsnList> ret = new ArrayList<>();
    	try(ZipFile zf = new ZipFile(jar.toFile())) {
    		for(Method mtd : lambdas) {
        		ZipEntry entry = zf.getEntry(mtd.getOwner().getNewName() + ".class");
        		if(entry == null) {
        			//class stripped?
        			ret.add(null);
        			continue;
        		}
        		ClassReader cr = new ClassReader(zf.getInputStream(entry));
        		ClassNode cn = new ClassNode();
        		cr.accept(cn, 0);
        		List<MethodNode> methods = cn.methods
        			.stream()
        			.filter(node -> {
        				return node.name.equals(mtd.getNewName()) && node.desc.equals(mtd.getNewDesc(mapper));
        			})
        			.collect(Collectors.toList());
        		
        		if(methods.size() > 1) {
        			throw new IllegalStateException("Could not find lambda instructions");	
        		}else if(methods.isEmpty()) {
        			//method stripped?, mostly gametest stuff
        			ret.add(null);
        		}else {
        			ret.add(methods.get(0).instructions);	
        		}
    		}
    	}catch(IOException e) {
    		throw new UncheckedIOException(e);
    	}
    	return ret;
    }

    public void addMapper(final IMapper mapper) {
        mappers.add(mapper);
    }

    private String mapClass(String cls) {
        for (IMapper mapper : mappers)
            cls = mapper.mapClass(cls);
        return cls;
    }

    private String mapField(String cls, String fld) {
        for (IMapper mapper : mappers) {
            fld = mapper.mapField(cls, fld);
            cls = mapper.mapClass(cls);
        }
        return fld;
    }

    private String mapMethod(String cls, String sig) {
        int idx = sig.indexOf('(');
        String mtd = sig.substring(0, idx);
        String desc = sig.substring(idx);
        for (IMapper mapper : mappers) {
            mtd = mapper.mapMethod(cls, mtd, desc);
            cls = mapper.mapClass(cls);
            desc = mapper.mapDescriptor(desc);
        }

        return mtd + desc;
    }
}
