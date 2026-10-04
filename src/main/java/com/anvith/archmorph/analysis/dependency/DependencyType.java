package com.anvith.archmorph.analysis.dependency;

/**
 * Represents the reason why two classes are connected in the dependency graph.
 * Each type carries a default weight used by the module affinity model.
 */
public enum DependencyType {

    /** Constructor injection / constructor parameter. */
    CONSTRUCTOR(1.0),

    /** Field (incl. injected fields and record components). */
    FIELD(1.0),

    /** Method parameter. */
    METHOD_PARAMETER(0.6),

    /** Method return type. */
    METHOD_RETURN(0.6),

    /** {@code userService.createUser(...)} — call on a project type. */
    METHOD_INVOCATION(0.8),

    /** {@code new User(...)}. */
    OBJECT_CREATION(0.7),

    /** extends. */
    INHERITANCE(0.9),

    /** implements. */
    IMPLEMENTATION(0.9),

    /** Project annotation or class literal inside an annotation. */
    ANNOTATION(0.4),

    /** Type argument, e.g. {@code List<User>} or {@code JpaRepository<User, Long>}. */
    GENERIC(0.7),

    /** JPA/Mongo relationship (@OneToMany, @ManyToOne, ...). */
    ENTITY_RELATIONSHIP(1.0),

    /** Local variables, casts, instanceof, class literals, static member access, throws. */
    TYPE_REFERENCE(0.4);

    private final double weight;

    DependencyType(double weight) {
        this.weight = weight;
    }

    /** Default weight of this dependency type in affinity calculations (0..1). */
    public double getWeight() {
        return weight;
    }
}
