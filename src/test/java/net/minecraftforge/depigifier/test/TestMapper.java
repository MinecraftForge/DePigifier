package net.minecraftforge.depigifier.test;

import net.minecraftforge.depigifier.IMapper;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class TestMapper {
    private List<IMapper> mappers = new ArrayList<>();
    @Test
    public void testMapper() {
        mappers.add(new TestIMapper("Input", "Output"));
        mappers.add(new TestIMapper("Cheese", "Pepper"));

        final Function<String, String> mapperFunction = getMapperFunction(IMapper::getClassNameTransformer);
        System.out.println(mapperFunction.apply("Input String"));
        System.out.println(mapperFunction.apply("Cheesy String"));
        System.out.println(mapperFunction.apply("Cheese String"));
    }

    public Function<String,String> getMapperFunction(Function<IMapper,Function<String,String>> mapperCall) {
        return s -> {
            String acc = s;
            for (IMapper mapper : mappers) {
                acc = mapperCall.apply(mapper).apply(acc);
            }
            return acc;
        };
    }

    public static class TestIMapper implements IMapper {
        private final Map<String,String> replacements = new HashMap<>();

        public TestIMapper(String input, String output) {
            replacements.put(input, output);
        }

        private Function<String,String> buildFunctionFromLookup(Map<String,String> lookup) {
            return s->lookup.keySet().stream().filter(s::startsWith).
                    findFirst().map(r->s.replaceFirst(r, lookup.get(r))).orElse(s);
        }
        @Override
        public Function<String, String> getClassNameTransformer() {
            return buildFunctionFromLookup(replacements);
        }

        @Override
        public Function<String, String> getMethodNameTransformer() {
            return null;
        }

        @Override
        public Function<String, String> getFieldNameTransformer() {
            return null;
        }
    }
}
