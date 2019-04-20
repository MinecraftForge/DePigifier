package net.minecraftforge.depigifier;

import com.machinezoo.noexception.Exceptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class TSRGSubstitutions implements IMapper {
    private final Path file;
    private final Map<String,String> classRenames = new HashMap<>();
    private final Map<String,String> methodRenames = new HashMap<>();
    private final Map<String,String> fieldRenames = new HashMap<>();

    public TSRGSubstitutions(final Path file) {
        this.file = file;
        load();
    }

    private void load() {
        final List<String> lines = Exceptions.sneak().get(() -> Files.readAllLines(this.file));
        for (String line: lines) {
            String[] parts = line.trim().split(" ");
            switch (parts.length) {
                case 2:
                    classRenames.put(parts[0], parts[1]);
                    break;
                case 3:
                    fieldRenames.put(parts[0]+parts[1], parts[2]);
                    break;
                case 4:
                    methodRenames.put(parts[0]+parts[1]+parts[2], parts[3]);
                    break;
            }
        }
    }

    private Function<String,String> buildFunctionFromLookup(final Map<String,String> lookup) {
        return s->lookup.keySet().stream().filter(s::startsWith).
                findFirst().map(r->s.replaceFirst(r, lookup.get(r))).orElse(s);
    }

    @Override
    public Function<String,String> getClassNameTransformer() {
        return buildFunctionFromLookup(classRenames);
    }

    @Override
    public Function<String,String> getMethodNameTransformer() {
        return buildFunctionFromLookup(methodRenames);
    }

    @Override
    public Function<String,String> getFieldNameTransformer() {
        return buildFunctionFromLookup(fieldRenames);
    }
}
