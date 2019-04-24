package net.minecraftforge.depigifier.test;

import net.minecraftforge.depigifier.IMapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;

public class TestMapper {
    private List<IMapper> mappers = new ArrayList<>();
    @Test
    public void testMapper() {
        mappers.add(new TestIMapper("Cheese", "Input"));
        mappers.add(new TestIMapper("Input", "Output"));

        final Function<String, String> mapperFunction = getMapperFunction(m -> m::mapClass);
        Assertions.assertEquals(mapperFunction.apply("Input/String"), "Output/String", "Simple package replace failed");
        Assertions.assertEquals(mapperFunction.apply("Cheesy/String"), "Cheesy/String", "Returned changed value when expected to be input");
        Assertions.assertEquals(mapperFunction.apply("Cheese/String"), "Output/String", "Chained transformation failed");
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

        private String mapPackage(String pkg) {
            String ret = replacements.get(pkg);
            if (ret != null)
                return ret;
            int idx = pkg.lastIndexOf('/');
            return idx == -1 ? pkg : mapPackage(pkg.substring(0, idx)) + pkg.substring(idx);
        }

        @Override
        public String mapClass(String cls) {
            int idx = cls.lastIndexOf('/');
            String pkg = mapPackage(idx == -1 ? "" : cls.substring(0, idx));
            return (pkg.isEmpty() ? "" : pkg + "/") + cls.substring(idx + 1);
        }

        @Override
        public String mapField(String cls, String field) {
            return field;
        }

        @Override
        public String mapMethod(String cls, String method, String desc) {
            return method;
        }
    }
}
