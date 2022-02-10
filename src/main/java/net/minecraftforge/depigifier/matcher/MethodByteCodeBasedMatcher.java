package net.minecraftforge.depigifier.matcher;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import net.minecraftforge.depigifier.model.Class;
import net.minecraftforge.depigifier.model.Method;
import net.minecraftforge.depigifier.model.Tree;
import net.minecraftforge.depigifier.util.ASMUtils;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MethodByteCodeBasedMatcher extends SignatureAndNameBasedMatcher
{

    private final Path oldJarPath;
    private final Path newJarPath;

    private final FileSystem oldJarFs;
    private final FileSystem newJarFs;

    public MethodByteCodeBasedMatcher(final Tree oldTree, final Tree newTree, final Path output, final Path oldJarPath, final Path newJarPath)
    {
        super(oldTree, newTree, output);

        this.oldJarPath = oldJarPath;
        this.newJarPath = newJarPath;

        try
        {
            this.oldJarFs = FileSystems.newFileSystem(this.oldJarPath, (ClassLoader) null);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Failed to create file system for old jar: " + this.oldJarPath, e);
        }

        try
        {
            this.newJarFs = FileSystems.newFileSystem(this.newJarPath, (ClassLoader) null);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Failed to create file system for new jar: " + this.newJarPath, e);
        }
    }

    private boolean doCompareMethodsUsingStaticContentAnalysis(final String[] oldMethod, final String[] newMethod)
    {
        return doCompareMethodsUsingStaticContentAnalysis(
          oldMethod,
          newMethod,
          new HashSet<>(),
          new HashSet<>()
        );
    }

    private boolean doCompareMethodsUsingStaticContentAnalysis(final String[] oldMethod, final String[] newMethod, final Set<String> recursivelyAnalyzedOldMethods, final Set<String> recursivelyAnalyzedNewMethods)
    {
        if (oldMethod == null || newMethod == null)
        {
            return false;
        }

        if (oldMethod.length == 1 || newMethod.length == 1)
        {
            if (oldMethod[0].isEmpty() || newMethod[0].isEmpty())
            {
                return false;
            }
        }

        if (oldMethod.length != newMethod.length)
        {
            return false;
        }

        for (int i = 0; i < oldMethod.length; i++)
        {
            if (!oldMethod[i].equals(newMethod[i]))
            {
                if (!doCompareUnequalMethodByteCodeStatement(oldMethod[i], newMethod[i], recursivelyAnalyzedOldMethods, recursivelyAnalyzedNewMethods))
                {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean doCompareUnequalMethodByteCodeStatement(
      final String oldStatement,
      final String newStatement,
      final Set<String> recursivelyAnalyzedOldMethods,
      final Set<String> recursivelyAnalyzedNewMethods)
    {
        //At the moment we only have special invocation checking for INVOKEVIRTUAL, anything else we currently consider simply not equal
        if (oldStatement.startsWith("INVOKEVIRTUAL"))
        {
            if (newStatement.startsWith("INVOKEVIRTUAL"))
            {
                //Both old and new method have an INVOKEVIRTUAL, we need to check if the method in the end does the same...
                final String oldInvocationTarget = oldStatement.replace("INVOKEVIRTUAL ", "");
                final String newInvocationTarget = newStatement.replace("INVOKEVIRTUAL ", "");

                if (recursivelyAnalyzedOldMethods.contains(oldInvocationTarget) && recursivelyAnalyzedNewMethods.contains(newInvocationTarget)) {
                    //Recursive call detection, this is the best we can do at the moment, if this breaks we can always return false here....
                    return true;
                }

                final String oldClass = oldInvocationTarget.substring(0, oldInvocationTarget.indexOf("."));
                final String newClass = newInvocationTarget.substring(0, newInvocationTarget.indexOf("."));

                final String oldSignature = oldInvocationTarget.replace(oldClass + ".", "");
                final String newSignature = newInvocationTarget.replace(newClass + ".", "");

                final String oldName = oldSignature.substring(0, oldSignature.indexOf(" "));
                final String newName = newSignature.substring(0, newSignature.indexOf(" "));

                final String oldDescriptor = oldSignature.replace(oldName + " ", "");
                final String newDescriptor = newSignature.replace(newName + " ", "");

                recursivelyAnalyzedOldMethods.add(oldInvocationTarget);
                recursivelyAnalyzedNewMethods.add(newInvocationTarget);

                doCompareMethodsUsingStaticContentAnalysis(
                  ASMUtils.getMethod(this.oldJarFs, oldClass, oldName, oldDescriptor),
                  ASMUtils.getMethod(this.newJarFs, newClass, newName, newDescriptor),
                  recursivelyAnalyzedOldMethods,
                  recursivelyAnalyzedNewMethods
                );
            }
        }

        //No invocations, process field access
        //Get field first.
        if (oldStatement.startsWith("GETFIELD")) {
            if (newStatement.startsWith("GETFIELD")) {
                return doCompareFieldAccessStatements("GETFIELD", oldStatement, newStatement);
            }
        }

        //Put field second.
        if (oldStatement.startsWith("PUTFIELD")) {
            if (newStatement.startsWith("PUTFIELD")) {
                return doCompareFieldAccessStatements("PUTFIELD", oldStatement, newStatement);
            }
        }

        return false;
    }

    private boolean doCompareFieldAccessStatements(final String opCode, final String oldStatement, final String newStatement)
    {
        final String oldField = oldStatement.replace(opCode + " ", "");
        final String newField = newStatement.replace(opCode + " ", "");

        final String oldClass = oldField.substring(0, oldField.indexOf("."));
        final String newClass = newField.substring(0, newField.indexOf("."));

        final String oldName = oldField.replace(oldClass + ".", "").substring(0, oldField.replace(oldClass + ".", "").indexOf(" "));
        final String newName = newField.replace(newClass + ".", "").substring(0, newField.replace(newClass + ".", "").indexOf(" "));

        return Objects.equals(this.oldTree.unmapClass(oldClass), this.newTree.unmapClass(newClass))
                 && Objects.equals(this.oldTree.unmapField(oldClass, oldName), this.newTree.unmapField(newClass, newName));
    }

    @Override
    protected void determineMethodDifferencesOf(
      final List<Method> newMethodTracked, final List<Method> missingMethods, final Class oldClass, final Class newClass, final BiFunction<Class, String, Method> methodGetter)
    {
        final List<Method> signatureNewMethods = new ArrayList<>();
        final List<Method> signatureMissingMethods = new ArrayList<>();

        super.determineMethodDifferencesOf(signatureNewMethods, signatureMissingMethods, oldClass, newClass, methodGetter);

        if (signatureNewMethods.isEmpty() && signatureMissingMethods.isEmpty())
        {
            return;
        }

        final Map<Method, String[]> oldMethodContents = oldClass.getMethods()
          .stream()
          .filter(method -> method.getOldName().contains("lambda$"))
          .collect(Collectors.toMap(
            Function.identity(),
            method -> ASMUtils.getMethod(oldJarFs, oldClass.getNewName(), method.getNewName(), method.getNewDesc(this.oldTree))
          ));

        final Multimap<String[], Method> newMethodContents = newClass.getMethods()
          .stream()
          .filter(method -> method.getOldName().contains("lambda$"))
          .collect(ImmutableListMultimap.toImmutableListMultimap(
            method -> ASMUtils.getMethod(newJarFs, newClass.getNewName(), Objects.requireNonNull(method).getNewName(), method.getNewDesc(this.newTree)),
            Function.identity()
          ));

        signatureMissingMethods.removeIf(missingMethod -> {
            final String[] oldMethod = oldMethodContents.get(missingMethod);

            final List<String[]> matchingCandidates = newMethodContents.keys()
                                                              .stream()
                                                              .filter(newMethod -> doCompareMethodsUsingStaticContentAnalysis(oldMethod, newMethod))
                                                              .collect(Collectors.toList());

            if (matchingCandidates.size() > 1) {
                matchingCandidates.removeIf(newMethod -> newMethodContents.get(newMethod).size() > 1);
            }

            if(matchingCandidates.size() == 1) {
                final String[] newMethod = matchingCandidates.get(0);
                final Collection<Method> newMethodCandidates = newMethodContents.get(newMethod); //This should be 1 :')

                if (newMethodCandidates.size() == 1) {
                    final Method newMethodCandidate = newMethodCandidates.iterator().next();
                    forcedMethods.put(newMethodCandidate, missingMethod);

                    signatureNewMethods.remove(newMethodCandidate);

                    return true;
                }
            }

            return false;
        });

        newMethodTracked.addAll(signatureNewMethods);
        missingMethods.addAll(signatureMissingMethods);
    }
}
