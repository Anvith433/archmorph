package com.anvith.archmorph.analysis.dependency.resolve;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Name-resolution scope of one compilation unit: package, imports and the
 * types it declares (including nested types).
 */
public final class TypeContext {

    private final String packageName;
    private final Map<String, String> singleImports = new HashMap<>();
    private final List<String> wildcardImports = new ArrayList<>();
    private final Map<String, String> staticMemberOwners = new HashMap<>();
    private final List<String> staticWildcardOwners = new ArrayList<>();
    private final Map<String, String> declaredTypes = new HashMap<>();

    private TypeContext(String packageName) {
        this.packageName = packageName;
    }

    public static TypeContext of(CompilationUnit cu) {
        TypeContext context = new TypeContext(cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse(""));
        for (ImportDeclaration imp : cu.getImports()) {
            String name = imp.getNameAsString();
            if (imp.isStatic()) {
                if (imp.isAsterisk()) {
                    context.staticWildcardOwners.add(name);
                } else if (name.contains(".")) {
                    context.staticMemberOwners.put(name.substring(name.lastIndexOf('.') + 1),
                            name.substring(0, name.lastIndexOf('.')));
                }
            } else if (imp.isAsterisk()) {
                context.wildcardImports.add(name);
            } else {
                context.singleImports.put(name.substring(name.lastIndexOf('.') + 1), name);
            }
        }
        for (TypeDeclaration<?> type : cu.getTypes()) {
            String qualified = context.packageName.isEmpty()
                    ? type.getNameAsString() : context.packageName + "." + type.getNameAsString();
            context.registerDeclared(type, qualified);
        }
        return context;
    }

    private void registerDeclared(TypeDeclaration<?> type, String qualified) {
        declaredTypes.putIfAbsent(type.getNameAsString(), qualified);
        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member instanceof TypeDeclaration<?> nested) {
                registerDeclared(nested, qualified + "." + nested.getNameAsString());
            }
        }
    }

    public String packageName() {
        return packageName;
    }

    public Map<String, String> singleImports() {
        return singleImports;
    }

    public List<String> wildcardImports() {
        return wildcardImports;
    }

    public Map<String, String> staticMemberOwners() {
        return staticMemberOwners;
    }

    public List<String> staticWildcardOwners() {
        return staticWildcardOwners;
    }

    public Map<String, String> declaredTypes() {
        return declaredTypes;
    }
}
