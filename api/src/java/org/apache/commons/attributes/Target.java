package org.apache.commons.attributes;

/**
 * Attribute indicating what elements an attribute may be applied to.
 * This is checked at runtime. If the attribute is absent, it defaults
 * to Target.ALL.
 *
 * <p>This attribute is intended to be used with attribute classes:
 * 
 * <pre><code>
 * / **
 *   * MyAttribute can only be applied to classes and fields, not methods.
 *   * @@Target(Target.CLASS | Target.FIELD)
 *   * /
 * public class MyAttribute { ... }
 * </code></pre>
 */
public class Target {
    
    /**
     * Indicates that the attribute can be applied to a class or interface.
     */
    public static final int CLASS = 1;
    
    /**
     * Indicates that the attribute can be applied to a field.
     */
    public static final int FIELD = 2;
    
    /**
     * Indicates that the attribute can be applied to a method.
     */
    public static final int METHOD = 4;
    
    /**
     * Indicates that the attribute can be applied to a constructor.
     */
    public static final int CONSTRUCTOR = 8;
    
    /**
     * Indicates that the attribute can be applied to a method parameter.
     */
    public static final int METHOD_PARAMETER = 16;
    
    /**
     * Indicates that the attribute can be applied to a constructor parameter.
     */
    public static final int CONSTRUCTOR_PARAMETER = 32;
    
    /**
     * Indicates that the attribute can be applied to a method return value.
     */
    public static final int RETURN = 64;
    
    /**
     * Indicates that the attribute can be applied to a parameter of a method or a constructor.
     * It is equal to <code>METHOD_PARAMETER | CONSTRUCTOR_PARAMETER</code>.
     */
    public static final int PARAMETER = METHOD_PARAMETER | CONSTRUCTOR_PARAMETER;
    
    /**
     * Indicates that the attribute can be applied to any program element.
     */
    public static final int ALL = CLASS | FIELD | METHOD | CONSTRUCTOR | PARAMETER | RETURN;
    
    private final int flags;
    
    /**
     * Creates a new target attribute.
     * 
     * @param flags a bitwise or of flags indicating the allowed targets.
     */
    public Target (int flags) {
        this.flags = flags;
    }
    
    /**
     * Returns an int that is the bitwise or of the allowed target flags.
     */
    public int getFlags () {
        return flags;
    }
    
}