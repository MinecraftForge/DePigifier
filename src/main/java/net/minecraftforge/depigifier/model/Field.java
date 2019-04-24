package net.minecraftforge.depigifier.model;

public class Field {
    private final Class owner;
    private final String oldName;
    private String newName;
    private String desc;

    public Field(final Class owner, final String name) {
        this.owner = owner;
        this.oldName = name;
        this.newName = name;
    }

    public Field setType(String desc) {
        this.desc = desc;
        return this;
    }

    public Field rename(String newName) {
        this.owner.renameField(this, newName);
        this.newName = newName;
        return this;
    }

    @Override
    public String toString() {
        return "(" + (this.desc == null ? "unknown" : this.desc) + ") " + owner.getOldName() + "." + oldName;
    }

    public String getOldName() {
        return oldName;
    }

    public String getNewName() {
        return newName;
    }

    public String getType() {
        return desc;
    }

    public Class getOwner() {
        return owner;
    }
}
