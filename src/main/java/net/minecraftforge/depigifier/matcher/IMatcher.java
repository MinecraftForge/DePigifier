package net.minecraftforge.depigifier.matcher;

import net.minecraftforge.depigifier.IMapper;

public interface IMatcher
{
    void computeClassListDifferences();

    void compareExistingClasses();

    void addMapper(IMapper mapper);
}
