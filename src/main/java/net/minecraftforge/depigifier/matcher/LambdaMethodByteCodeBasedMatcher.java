package net.minecraftforge.depigifier.matcher;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import net.minecraftforge.depigifier.model.Class;
import net.minecraftforge.depigifier.model.Method;
import net.minecraftforge.depigifier.model.Tree;
import net.minecraftforge.depigifier.util.ASMUtils;
import net.minecraftforge.depigifier.util.GroupingUtils;
import org.checkerframework.checker.units.qual.A;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LambdaMethodByteCodeBasedMatcher extends SignatureAndNameBasedMatcher
{
    private static final Set<String> SPECIAL_METHOD_SIGNATURES = ImmutableSet.of(
      "toString()Ljava/lang/String;"
    );
    private static final Set<String> SYSTEM_METHODS = ImmutableSet.of(
      "java/lang/Enum ordinal ()I"
    );
    private static int MAX_RECURSION_DEPTH = 0;
    private final FileSystem oldJarFs;
    private final FileSystem newJarFs;

    private int rematchedLambdas = 0;
    private int namedMappedLambdas = 0;
    private int removedLambdas = 0;
    private int newLambdas = 0;
    private int foundLambdas = 0;

    public LambdaMethodByteCodeBasedMatcher(final Tree oldTree, final Tree newTree, final Path output, final Path oldJarPath, final Path newJarPath)
    {
        super(oldTree, newTree, output);

        try
        {
            this.oldJarFs = FileSystems.newFileSystem(oldJarPath, (ClassLoader) null);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Failed to create file system for old jar: " + oldJarPath, e);
        }

        try
        {
            this.newJarFs = FileSystems.newFileSystem(newJarPath, (ClassLoader) null);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Failed to create file system for new jar: " + newJarPath, e);
        }
    }

    @Override
    protected void outputAdditionalStatistics()
    {
        super.outputAdditionalStatistics();
        System.out.println("Lambda: " + removedLambdas + "/" + newLambdas + "/" + foundLambdas);
        System.out.println("==========================");
        System.out.println("Via Name / Via ByteCode");
        System.out.println("Matching: " + namedMappedLambdas + "/" + rematchedLambdas);
    }

    @Override
    protected void determineMethodDifferencesOf(
      final List<Method> newMethodTracked, final List<Method> missingMethods, final Class oldClass, final Class newClass, final BiFunction<Class, String, Method> methodGetter)
    {
        final List<Method> signatureNewMethods = new ArrayList<>();
        final List<Method> signatureMissingMethods = new ArrayList<>();

        //Run the diff only on the none lambda methods.
        differenceSet(
          () -> oldClass.getMethodSignatures().stream().filter(sig -> !sig.contains("lambda$")).collect(Collectors.toSet()),
          () -> newClass.getMethodSignatures().stream().filter(sig -> !sig.contains("lambda$")).collect(Collectors.toSet()),
          s -> mapMethod(oldClass.getOldName(), s),
          sig -> methodGetter.apply(oldClass, sig),
          sig -> methodGetter.apply(newClass, sig),
          forcedMethods::put,
          () -> signatureNewMethods,
          ArrayList::new,
          () -> signatureMissingMethods);

        signatureMissingMethods.stream().filter(m -> !m.getOldName().contains("lambda$")).forEach(missingMethods::add);
        signatureNewMethods.stream().filter(m -> !m.getOldName().contains("lambda$")).forEach(newMethods::add);

        final List<Method> signatureNewLambdas = new ArrayList<>();
        final List<Method> signatureMissingLambdas = new ArrayList<>();

        foundLambdas += oldClass.getMethodSignatures().stream().filter(sig -> sig.contains("lambda$")).count();

        remapShiftedLambdaMethods(
          oldClass.getMethodSignatures().stream().filter(sig -> sig.contains("lambda$")).collect(Collectors.toSet()),
          newClass.getMethodSignatures().stream().filter(sig -> sig.contains("lambda$")).collect(Collectors.toSet()),
          oldClass,
          newClass,
          methodGetter,
          signatureNewLambdas,
          signatureMissingLambdas
        );

        final Set<String> postProcessingOldLambdas = signatureMissingLambdas.stream().filter(m -> m.getOldName().contains("lambda$")).map(method -> method.getOldName() + method.getOldDesc()).collect(Collectors.toSet());
        final Set<String> postProcessingNewLambdas = signatureNewLambdas.stream().filter(m -> m.getOldName().contains("lambda$")).map(method -> method.getOldName() + method.getOldDesc()).collect(Collectors.toSet());

        final List<Method> remainderNewLambdas = new ArrayList<>();
        final List<Method> missingOldLambdas = new ArrayList<>();
        final List<Method> commonLambdas = new ArrayList<>();

        //Run the diff now on the missing lambdas.
        differenceSet(
          () -> postProcessingOldLambdas,
          () -> postProcessingNewLambdas,
          s -> mapMethod(oldClass.getOldName(), s),
          sig -> methodGetter.apply(oldClass, sig),
          sig -> methodGetter.apply(newClass, sig),
          forcedMethods::put,
          () -> remainderNewLambdas,
          () -> commonLambdas,
          () -> missingOldLambdas);

        namedMappedLambdas += commonLambdas.size();

        newLambdas += remainderNewLambdas.size();
        removedLambdas += missingOldLambdas.size();

        newMethodTracked.addAll(remainderNewLambdas);
        missingMethods.addAll(missingOldLambdas);
    }

    private void remapShiftedLambdaMethods(
      final Set<String> oldLambdaSignatures,
      final Set<String> newLambdaSignatures,
      final Class oldClass,
      final Class newClass,
      final BiFunction<Class, String, Method> methodGetter,
      final List<Method> signatureNewMethods,
      final List<Method> signatureMissingMethods
    )
    {
        final List<Method> oldLambdaMethods = oldLambdaSignatures.stream().map(sig -> methodGetter.apply(oldClass, sig)).collect(Collectors.toList());
        final Multimap<String, Method> prefixedOldLambdas = GroupingUtils.groupByUsingSet(
          oldLambdaMethods,
          method -> method.getOldName().substring(0, method.getOldName().lastIndexOf('$'))
        );

        final List<Method> newLambdaMethods = newLambdaSignatures.stream().map(sig -> methodGetter.apply(newClass, sig)).collect(Collectors.toList());
        final Multimap<String, Method> prefixedNewLambdas = GroupingUtils.groupByUsingSet(
          newLambdaMethods,
          method -> method.getOldName().substring(0, method.getOldName().lastIndexOf('$'))
        );

        prefixedOldLambdas.keySet().forEach(prefix -> {
            final List<Method> oldLambdaPrefixed = new ArrayList<>(prefixedOldLambdas.get(prefix));
            final List<Method> newLambdaPrefixed = new ArrayList<>(prefixedNewLambdas.get(prefix));

            //Lambdas can shift in index number based on the class they are in.
            //So lambda$1 and lambda$2 can become lambda$3 and lambda$4 in the new version, even though they are the exact same method.
            if (handleLambdaShifting(oldLambdaPrefixed, newLambdaPrefixed))
            {
                oldLambdaMethods.removeAll(oldLambdaPrefixed);
                newLambdaMethods.removeAll(newLambdaPrefixed);

                rematchedLambdas += oldLambdaPrefixed.size();
            }
            else
            {
                final List<Method> oldLambdasMatched = new ArrayList<>();
                final List<Method> newLambdasMatched = new ArrayList<>();

                handleLambdaNameChanging(oldLambdaPrefixed, newLambdaPrefixed, oldLambdasMatched, newLambdasMatched);

                if (oldLambdasMatched.size() > 0) {
                    if (oldLambdasMatched.size() != newLambdasMatched.size()) {
                        throw new IllegalArgumentException("Failed to match lambdas with the same name, but different number of parameters.");
                    }

                    oldLambdaMethods.removeAll(oldLambdasMatched);
                    newLambdaMethods.removeAll(newLambdasMatched);
                }
            }
        });

        attemptLambdaRematching(oldLambdaMethods, newLambdaMethods, signatureNewMethods, signatureMissingMethods);
    }

    private boolean handleLambdaShifting(final List<Method> oldLambdaPrefixed, final List<Method> newLambdaPrefixed)
    {
        if (oldLambdaPrefixed.size() == newLambdaPrefixed.size())
        {
            final List<Method> oldLambdaPrefixedSorted = oldLambdaPrefixed.stream().sorted(Comparator.comparing(this::getLambdaIndex)).collect(Collectors.toList());
            final List<Method> newLambdaPrefixedSorted = newLambdaPrefixed.stream().sorted(Comparator.comparing(this::getLambdaIndex)).collect(Collectors.toList());

            boolean allMatching = true;
            for (int i = 0; i < oldLambdaPrefixedSorted.size(); i++)
            {
                final Method oldLambda = oldLambdaPrefixedSorted.get(i);
                final Method newLambda = newLambdaPrefixedSorted.get(i);

                if (!doCompareMethodsUsingStaticContentAnalysis(oldLambda, newLambda))
                {
                    allMatching = false;
                }
            }

            if (allMatching)
            {
                for (int i = 0; i < oldLambdaPrefixedSorted.size(); i++)
                {
                    final Method oldLambda = oldLambdaPrefixedSorted.get(i);
                    final Method newLambda = newLambdaPrefixedSorted.get(i);

                    forcedMethods.put(newLambda, oldLambda);
                }
                return true;
            }
        }

        return false;
    }

    private void handleLambdaNameChanging(final List<Method> oldLambdaPrefixed, final List<Method> newLambdaPrefixed, final List<Method> oldLambdasMapped, final List<Method> newLambdasMapped)
    {
        final List<Method> oldLambdaPrefixedSorted = oldLambdaPrefixed.stream().sorted(Comparator.comparing(this::getLambdaIndex)).collect(Collectors.toList());
        final List<Method> newLambdaPrefixedSorted = newLambdaPrefixed.stream().sorted(Comparator.comparing(this::getLambdaIndex)).collect(Collectors.toList());

        for (final Method oldLambda : oldLambdaPrefixedSorted)
        {
            Method matchingLambda = null;
            for (final Method newLambda : newLambdaPrefixedSorted) {
                if (doCompareMethodsUsingStaticContentAnalysis(oldLambda, newLambda))
                {
                    matchingLambda = newLambda;
                    break;
                }
            }

            if (matchingLambda != null) {
                newLambdaPrefixedSorted.remove(matchingLambda);

                oldLambdasMapped.add(oldLambda);
                newLambdasMapped.add(matchingLambda);

                forcedMethods.put(matchingLambda, oldLambda);
                rematchedLambdas++;
            }
        }
    }



    private void attemptLambdaRematching(
      final List<Method> oldLambdaMethods,
      final List<Method> newLambdaMethods,
      final List<Method> signatureNewMethods,
      final List<Method> signatureMissingMethods)
    {
        final Map<Method, ASMUtils.ASMMethodData> oldMethodContents = oldLambdaMethods
          .stream()
          .collect(Collectors.toMap(
            Function.identity(),
            method -> ASMUtils.getMethod(oldJarFs, method.getOwner().getNewName(), method.getNewName(), method.getNewDesc(this.oldTree))
          ));

        final Multimap<ASMUtils.ASMMethodData, Method> newMethodContents = newLambdaMethods
          .stream()
          .collect(ImmutableListMultimap.toImmutableListMultimap(
            method -> ASMUtils.getMethod(newJarFs, method.getOwner().getNewName(), Objects.requireNonNull(method).getNewName(), method.getNewDesc(this.newTree)),
            Function.identity()
          ));

        oldLambdaMethods.removeIf(missingMethod -> {
            final ASMUtils.ASMMethodData oldMethod = oldMethodContents.get(missingMethod);

            final List<ASMUtils.ASMMethodData> matchingCandidates = newMethodContents.keys()
              .stream()
              .filter(newMethod -> doCompareMethodsUsingStaticContentAnalysis(oldMethod, newMethod))
              .collect(Collectors.toList());

            if (matchingCandidates.size() > 1)
            {
                matchingCandidates.removeIf(newMethod -> newMethodContents.get(newMethod).size() > 1);
            }

            if (matchingCandidates.size() == 1)
            {
                final ASMUtils.ASMMethodData newMethod = matchingCandidates.get(0);
                final Collection<Method> newMethodCandidates = newMethodContents.get(newMethod); //This should be 1 :')

                if (newMethodCandidates.size() == 1)
                {
                    final Method newMethodCandidate = newMethodCandidates.iterator().next();
                    if (newLambdaMethods.contains(newMethodCandidate))
                    {
                        forcedMethods.put(newMethodCandidate, missingMethod);

                        rematchedLambdas++;

                        newLambdaMethods.remove(newMethodCandidate);
                        return true;
                    }
                }
            }

            return false;
        });

        signatureMissingMethods.addAll(oldLambdaMethods);
        signatureNewMethods.addAll(newLambdaMethods);
    }

    private int getLambdaIndex(final Method lambda)
    {
        final String remainedIndex = lambda.getOldName().substring(lambda.getOldName().lastIndexOf('$') + 1);
        return Integer.parseInt(remainedIndex);
    }

    private boolean doCompareMethodsUsingStaticContentAnalysis(final Method oldMethod, final Method newMethod)
    {
        return doCompareMethodsUsingStaticContentAnalysis(
          ASMUtils.getMethod(oldJarFs, oldMethod.getOwner().getNewName(), oldMethod.getNewName(), oldMethod.getNewDesc(this.oldTree)),
          ASMUtils.getMethod(newJarFs, newMethod.getOwner().getNewName(), newMethod.getNewName(), newMethod.getNewDesc(this.newTree)),
          new HashSet<>(),
          new HashSet<>(),
          0
        );
    }

    private boolean doCompareMethodsUsingStaticContentAnalysis(final ASMUtils.ASMMethodData oldMethod, final ASMUtils.ASMMethodData newMethod)
    {
        return doCompareMethodsUsingStaticContentAnalysis(
          oldMethod,
          newMethod,
          new HashSet<>(),
          new HashSet<>(),
          0
        );
    }

    private boolean doCompareMethodsUsingStaticContentAnalysis(
      final ASMUtils.ASMMethodData oldMethod,
      final ASMUtils.ASMMethodData newMethod,
      final Set<String> recursivelyAnalyzedOldMethods,
      final Set<String> recursivelyAnalyzedNewMethods,
      final int recursionDepth)
    {
        if (oldMethod == null || newMethod == null)
        {
            return false;
        }

        if (!oldMethod.wasFound() || !newMethod.wasFound())
        {
            return oldMethod.getClassName().equals(newMethod.getClassName()) && oldMethod.getMethodName().equals(newMethod.getMethodName()) &&
                     oldMethod.getMethodDescriptor().equals(newMethod.getMethodDescriptor()) &&
                     SYSTEM_METHODS.contains(oldMethod.getClassName() + " " + oldMethod.getMethodName() + " " + oldMethod.getMethodDescriptor());
        }

        if ((oldMethod.getAccessFlags() & Opcodes.ACC_ABSTRACT) != 0 || (newMethod.getAccessFlags() & Opcodes.ACC_ABSTRACT) != 0)
        {
            //We have an invocation to a class method which is abstract.
            //There is no code we can analyze, so we compare the names as with fields

            return Objects.equals(this.oldTree.unmapMethod(oldMethod.getClassName(), oldMethod.getMethodName(), oldMethod.getMethodDescriptor()),
              this.newTree.unmapMethod(newMethod.getClassName(), newMethod.getMethodName(), newMethod.getMethodDescriptor()));
        }

        if (oldMethod.getByteCode().length != newMethod.getByteCode().length)
        {
            return false;
        }

        for (int i = 0; i < oldMethod.getByteCode().length; i++)
        {
            final String[] oldStatements = getInstructionStatement(oldMethod, i);
            final String[] newStatements = getInstructionStatement(newMethod, i);


            if (!Arrays.equals(oldStatements, newStatements))
            {
                if (!doCompareUnequalMethodByteCodeStatement(oldStatements, newStatements, recursivelyAnalyzedOldMethods, recursivelyAnalyzedNewMethods, recursionDepth))
                {
                    return false;
                }
            }

            i += oldStatements.length - 1;
        }

        return true;
    }

    private String[] getInstructionStatement(ASMUtils.ASMMethodData source, int offset)
    {
        if (!source.getByteCode()[offset].endsWith("["))
        {
            return new String[] {source.getByteCode()[offset]};
        }

        final List<String> instruction = new ArrayList<>();
        while (offset < source.getByteCode().length)
        {
            instruction.add(source.getByteCode()[offset]);
            if (source.getByteCode()[offset].startsWith("]"))
            {
                break;
            }

            offset++;
        }

        return instruction.toArray(new String[0]);
    }

    private boolean doCompareUnequalMethodByteCodeStatement(
      final String[] oldStatement,
      final String[] newStatement,
      final Set<String> recursivelyAnalyzedOldMethods,
      final Set<String> recursivelyAnalyzedNewMethods,
      final int recursionDepth)
    {
        final String oldInitialStatement = oldStatement[0];
        final String newInitialStatement = newStatement[0];

        //Ignore frames.
        if (oldInitialStatement.startsWith("FRAME") && newInitialStatement.startsWith("FRAME"))
        {
            return true;
        }

        //Check for object instantiation.
        if (oldInitialStatement.startsWith("NEW") && newInitialStatement.startsWith("NEW"))
        {
            final String oldClassName = oldInitialStatement.substring(4);
            final String newClassName = newInitialStatement.substring(4);

            return Objects.equals(this.oldTree.unmapClass(oldClassName), this.newTree.unmapClass(newClassName));
        }

        //We need to handle special invoke statements here, since they contain method references and those need to be handled.
        if (oldInitialStatement.startsWith("INVOKEVIRTUAL") && newInitialStatement.startsWith("INVOKEVIRTUAL"))
        {
            return doCompareInvokeStatements(
              "INVOKEVIRTUAL",
              oldInitialStatement,
              newInitialStatement,
              recursivelyAnalyzedOldMethods,
              recursivelyAnalyzedNewMethods,
              recursionDepth + 1,
              false);
        }

        if (oldInitialStatement.startsWith("INVOKESTATIC") && newInitialStatement.startsWith("INVOKESTATIC"))
        {
            return doCompareInvokeStatements(
              "INVOKESTATIC",
              oldInitialStatement,
              newInitialStatement,
              recursivelyAnalyzedOldMethods,
              recursivelyAnalyzedNewMethods,
              recursionDepth + 1,
              false);
        }

        if (oldInitialStatement.startsWith("INVOKESPECIAL") && newInitialStatement.startsWith("INVOKESPECIAL"))
        {
            return doCompareInvokeStatements(
              "INVOKESPECIAL",
              oldInitialStatement,
              newInitialStatement,
              recursivelyAnalyzedOldMethods,
              recursivelyAnalyzedNewMethods,
              recursionDepth + 1,
              false);
        }

        if (oldInitialStatement.startsWith("INVOKEDYNAMIC") && newInitialStatement.startsWith("INVOKEDYNAMIC"))
        {
            return doCompareInvokeDynamicStatements(
              oldStatement,
              newStatement,
              recursivelyAnalyzedOldMethods,
              recursivelyAnalyzedNewMethods,
              recursionDepth + 1);
        }

        //No invocations, process field access
        //Get field first.
        if (oldInitialStatement.startsWith("GETFIELD") && newInitialStatement.startsWith("GETFIELD"))
        {
            return doCompareFieldAccessStatements("GETFIELD", oldInitialStatement, newInitialStatement);
        }

        //Put field second.
        if (oldInitialStatement.startsWith("PUTFIELD") && newInitialStatement.startsWith("PUTFIELD"))
        {
            return doCompareFieldAccessStatements("PUTFIELD", oldInitialStatement, newInitialStatement);
        }

        //Get static.
        if (oldInitialStatement.startsWith("GETSTATIC") && newInitialStatement.startsWith("GETSTATIC"))
        {
            return doCompareFieldAccessStatements("GETSTATIC", oldInitialStatement, newInitialStatement);
        }

        //Put static.
        if (oldInitialStatement.startsWith("PUTSTATIC") && newInitialStatement.startsWith("PUTSTATIC"))
        {
            return doCompareFieldAccessStatements("PUTSTATIC", oldInitialStatement, newInitialStatement);
        }

        //Casts.
        if (oldInitialStatement.startsWith("CHECKCAST") && newInitialStatement.startsWith("CHECKCAST"))
        {
            return doCompareCastStatements("CHECKCAST", oldInitialStatement, newInitialStatement);
        }

        //Invoke interface method
        if (oldInitialStatement.startsWith("INVOKEINTERFACE") && newInitialStatement.startsWith("INVOKEINTERFACE")) {
            return doCompareInterfaceInvocation("INVOKEINTERFACE", oldInitialStatement, newInitialStatement);
        }

        //Load constants (for type handling)
        if (oldInitialStatement.startsWith("LDC") && newInitialStatement.startsWith("LDC")) {
            return doCompareLoadConstants("LDC", oldInitialStatement, newInitialStatement);
        }

        //Try Catch with Resources.
        if (oldInitialStatement.startsWith("TRYCATCHBLOCK") && newInitialStatement.startsWith("TRYCATCHBLOCK")) {
            return doCompareTryCatchBlockStatements("TRYCATCHBLOCK", oldInitialStatement, newInitialStatement);
        }

        //New array.
        if (oldInitialStatement.startsWith("ANEWARRAY") && newInitialStatement.startsWith("ANEWARRAY")) {
            return doCompareNewArrayStatements("ANEWARRAY", oldInitialStatement, newInitialStatement);
        }

        return false;
    }

    private boolean doCompareInvokeStatements(
      final String opCode,
      final String oldStatement,
      final String newStatement,
      final Set<String> recursivelyAnalyzedOldMethods,
      final Set<String> recursivelyAnalyzedNewMethods,
      final int recursionDepth,
      boolean fromDynamicInvoke)
    {
        //Both old and new method have an INVOKEVIRTUAL, we need to check if the method in the end does the same...
        final String oldInvocationTarget = oldStatement.replace(opCode + " ", "");
        final String newInvocationTarget = newStatement.replace(opCode + " ", "");

        if (oldInvocationTarget.equals(newInvocationTarget))
        {
            return true;
        }

        if (recursivelyAnalyzedOldMethods.contains(oldInvocationTarget) && recursivelyAnalyzedNewMethods.contains(newInvocationTarget))
        {
            //Recursive call detection, this is the best we can do at the moment, if this breaks we can always return false here....
            return true;
        }

        final String oldClass = oldInvocationTarget.substring(0, oldInvocationTarget.indexOf("."));
        final String newClass = newInvocationTarget.substring(0, newInvocationTarget.indexOf("."));

        final String oldSignature = oldInvocationTarget.replace(oldClass + ".", "");
        final String newSignature = newInvocationTarget.replace(newClass + ".", "");

        if (!oldClass.equals(newClass) && oldSignature.equals(newSignature) && SPECIAL_METHOD_SIGNATURES.contains(oldSignature))
        {
            //Special snowflake method, those are build in system methods in general, and we consider them the same
            //Handles new overrides of toString() for example.
            return true;
        }

        final String oldName = oldSignature.substring(0, oldSignature.indexOf(fromDynamicInvoke ? "(" : " "));
        final String newName = newSignature.substring(0, newSignature.indexOf(fromDynamicInvoke ? "(" : " "));

        final String oldDescriptor =
          oldSignature.substring((oldName + (fromDynamicInvoke ? "" : " ")).length()); // oldSignature.replace(oldName + (fromDynamicInvoke ? "" : " "), "");
        final String newDescriptor =
          newSignature.substring((newName + (fromDynamicInvoke ? "" : " ")).length()); // newSignature.replace(newName + (fromDynamicInvoke ? "" : " "), "");

        if (!oldClass.equals(newClass) && oldSignature.startsWith("<init>"))
        {
            //Constructors... We consider them equal if the class maps to each other:
            return
              Objects.equals(this.oldTree.unmapDescriptor(oldDescriptor), this.newTree.unmapDescriptor(newDescriptor)) &&
                Objects.equals(this.oldTree.unmapClass(oldClass), this.newTree.unmapClass(newClass));
        }

        recursivelyAnalyzedOldMethods.add(oldInvocationTarget);
        recursivelyAnalyzedNewMethods.add(newInvocationTarget);

        //If we are exceeding the recursion depth, we assume that the method is just equal
        //We just don't care if the original lambda is different so deep in the call stack so we just compare the name
        if (recursionDepth > MAX_RECURSION_DEPTH)
        {
            return Objects.equals(this.oldTree.unmapMethod(oldClass, oldName, oldDescriptor),
              this.newTree.unmapMethod(newClass, newName, newDescriptor));
        }

        return doCompareMethodsUsingStaticContentAnalysis(
          ASMUtils.getMethod(this.oldJarFs, oldClass, oldName, oldDescriptor),
          ASMUtils.getMethod(this.newJarFs, newClass, newName, newDescriptor),
          recursivelyAnalyzedOldMethods,
          recursivelyAnalyzedNewMethods,
          recursionDepth
        );
    }

    private boolean doCompareInvokeDynamicStatements(
      final String[] oldStatement,
      final String[] newStatement,
      final Set<String> recursivelyAnalyzedOldMethods,
      final Set<String> recursivelyAnalyzedNewMethods,
      final int recursionDepth)
    {
        final List<String> oldStatementList = Arrays.asList(oldStatement);
        final List<String> newStatementList = Arrays.asList(newStatement);

        final int oldInvokeVirtualLabelIndex =
          oldStatementList.stream()
            .filter(s ->
                      s.startsWith("// handle kind 0x5 : INVOKEVIRTUAL") ||
                        s.startsWith("// handle kind 0x9 : INVOKEINTERFACE"))
            .findFirst()
            .map(oldStatementList::indexOf)
            .map(index -> index + 1)
            .orElse(-1);
        final int newInvokeVirtualLabelIndex =
          newStatementList.stream()
            .filter(s ->
                      s.startsWith("// handle kind 0x5 : INVOKEVIRTUAL") ||
                           s.startsWith("// handle kind 0x9 : INVOKEINTERFACE"))
            .findFirst()
            .map(newStatementList::indexOf)
            .map(index -> index + 1)
            .orElse(-1);

        if (newInvokeVirtualLabelIndex == -1 || oldInvokeVirtualLabelIndex != newInvokeVirtualLabelIndex || oldInvokeVirtualLabelIndex >= oldStatementList.size()
              || newInvokeVirtualLabelIndex >= newStatementList.size())
        {
            return false; //Invoke virtual is in the wrong spot for the method handle. So we treat them as not equal;
        }

        String oldHandleTargetInvocation = oldStatementList.get(oldInvokeVirtualLabelIndex);
        String newHandleTargetInvocation = newStatementList.get(newInvokeVirtualLabelIndex);

        if (oldHandleTargetInvocation.endsWith(" itf,")) {
            oldHandleTargetInvocation = oldHandleTargetInvocation.substring(0, oldHandleTargetInvocation.length() - 5) + ",";
        }

        if (newHandleTargetInvocation.endsWith(" itf,")) {
            newHandleTargetInvocation = newHandleTargetInvocation.substring(0, newHandleTargetInvocation.length() - 5) + ",";
        }

        return doCompareInvokeStatements(
          "INVOKEVIRTUAL",
          oldHandleTargetInvocation.substring(0, oldHandleTargetInvocation.lastIndexOf(",")),
          newHandleTargetInvocation.substring(0, newHandleTargetInvocation.lastIndexOf(",")),
          recursivelyAnalyzedOldMethods,
          recursivelyAnalyzedNewMethods,
          recursionDepth,
          true);
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

    private boolean doCompareCastStatements(final String opCode, final String oldStatement, final String newStatement)
    {
        final String oldClass = oldStatement.replace(opCode + " ", "");
        final String newClass = newStatement.replace(opCode + " ", "");

        return Objects.equals(this.oldTree.unmapClass(oldClass), this.newTree.unmapClass(newClass));
    }

    private boolean doCompareInterfaceInvocation(final String opCode, final String oldStatement, final String newStatement)
    {
        String oldInterface = oldStatement.replace(opCode + " ", "");
        String newInterface = newStatement.replace(opCode + " ", "");

        oldInterface = oldInterface.substring(0, oldInterface.length() - 6); // Strips " (itf)" from the end.
        newInterface = newInterface.substring(0, newInterface.length() - 6);

        final String oldClass = oldInterface.substring(0, oldInterface.indexOf("."));
        final String newClass = newInterface.substring(0, newInterface.indexOf("."));

        final String oldSignature = oldInterface.replace(oldClass + ".", "");
        final String newSignature = newInterface.replace(newClass + ".", "");

        final String oldName = oldInterface.replace(oldClass + ".", "").substring(0, oldInterface.replace(oldClass + ".", "").indexOf(" "));
        final String newName = newInterface.replace(newClass + ".", "").substring(0, newInterface.replace(newClass + ".", "").indexOf(" "));

        final String oldDescriptor =
          oldSignature.substring((oldName + " ").length());
        final String newDescriptor =
          newSignature.substring((newName + " ").length());

        return doCompareInterfaceInvocationTarget(oldClass, newClass, oldName, newName, oldDescriptor, newDescriptor);
    }

    private boolean doCompareInterfaceInvocationTarget(String oldClass, String newClass, String oldName, String newName, String oldDescriptor, String newDescriptor)
    {
        final String oldUnmappedName = this.oldTree.unmapMethod(oldClass, oldName, oldDescriptor);
        final String newUnmappedName = this.newTree.unmapMethod(newClass, newName, newDescriptor);

        if (Objects.equals(oldUnmappedName, oldName) && Objects.equals(newUnmappedName, newName)) {
            //Likely means this is related to super type methods, handle this.
            final String[] oldSuperInterfaces = ASMUtils.getInterfaces(this.oldJarFs, oldClass);
            final String[] newSuperInterfaces = ASMUtils.getInterfaces(this.newJarFs, newClass);

            for (final String oldSuperInterface : oldSuperInterfaces) {
                for (final String newSuperInterface : newSuperInterfaces) {
                    if (doCompareInterfaceInvocationTarget(oldSuperInterface, newSuperInterface, oldName, newName, oldDescriptor, newDescriptor)) {
                        return true;
                    }
                }
            }
        }

        return Objects.equals(this.oldTree.unmapMethod(oldClass, oldName, oldDescriptor), this.newTree.unmapMethod(newClass, newName, newDescriptor));
    }

    private boolean doCompareLoadConstants(final String opCode, final String oldStatement, final String newStatement)
    {
        final String oldValue = oldStatement.replace(opCode + " ", "");
        final String newValue = newStatement.replace(opCode + " ", "");

        if (oldValue.endsWith(".class") && newValue.endsWith(".class")) {
            final String oldClass = oldValue.substring(0, oldValue.length() - 6);
            final String newClass = newValue.substring(0, newValue.length() - 6);

            return Objects.equals(this.oldTree.unmapClass(oldClass.substring(1).replace(";", "")), this.newTree.unmapClass(newClass.substring(1).replace(";", "")));
        }

        return true;
    }

    private boolean doCompareTryCatchBlockStatements(final String opCode, final String oldStatement, final String newStatement) {
        final String oldClass = oldStatement.substring(oldStatement.lastIndexOf(" ") + 1);
        final String newClass = newStatement.substring(newStatement.lastIndexOf(" ") + 1);

        return Objects.equals(this.oldTree.unmapClass(oldClass), this.newTree.unmapClass(newClass));
    }

    private boolean doCompareNewArrayStatements(final String opCode, final String oldStatement, final String newStatement) {
        final String oldClass = oldStatement.substring(oldStatement.lastIndexOf(" ") + 1);
        final String newClass = newStatement.substring(newStatement.lastIndexOf(" ") + 1);

        return Objects.equals(this.oldTree.unmapClass(oldClass), this.newTree.unmapClass(newClass));
    }
}
