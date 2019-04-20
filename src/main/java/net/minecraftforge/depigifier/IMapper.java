package net.minecraftforge.depigifier;

import java.util.function.Function;

public interface IMapper {
    Function<String,String> getClassNameTransformer();

    Function<String,String> getMethodNameTransformer();

    Function<String,String> getFieldNameTransformer();
}
