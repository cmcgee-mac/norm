/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;

import static com.github.cmcgeemac.norm.AbstractStatement.VARIABLE_PATTERN;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

/**
 * Puts certain compile-time constraints on the SQL annotation and performs code
 * generation for improved runtime performance. This class is not intended for
 * external use. It is installed automatically as a service in the compiler.
 */
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("com.github.cmcgeemac.norm.SQL")
public class SQLStatementProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        Messager messager = processingEnv.getMessager();
        Filer filer = processingEnv.getFiler();

        for (TypeElement typeElement : annotations) {
            for (Element el : env.getElementsAnnotatedWith(typeElement)) {
                if (!(el instanceof TypeElement)) {
                    continue;
                }

                TypeElement statementElement = (TypeElement) el;

                SQL sqlAnn = statementElement.getAnnotation(SQL.class);
                Set<String> referencedParms = new HashSet<>();
                Set<String> dereferencedParms = new HashSet<>();

                String safeSQL = "";

                try {
                    Statement sqlParsed = CCJSqlParserUtil.parse(sqlAnn.value());
                    Util.visitJdbcParameters(sqlParsed, p -> {
                        referencedParms.add(p.getName());
                        return "@@@" + p.getName() + "@@@";
                    });
                    safeSQL = Util.statementToString(sqlParsed);
                } catch (JSQLParserException ex) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                            "@SQL annotation has a bad SQL statement: " + ex.getMessage(),
                            statementElement);
                }

                TypeMirror superClass = statementElement.getSuperclass();

                if (superClass instanceof NoType) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                            "@SQL annotation only applies to NORM statement subclasses",
                            statementElement);
                    continue;
                }

                if (superClass instanceof DeclaredType) {
                    DeclaredType statementSuperType = (DeclaredType) superClass;
                    Element statementSuperTypeElem = statementSuperType.asElement();

                    if (!statementSuperTypeElem.getSimpleName().contentEquals("NormStatement")
                            && !statementSuperTypeElem.getSimpleName().contentEquals("NormStatementWithResult")) {

                        messager.printMessage(Diagnostic.Kind.ERROR,
                                "@SQL annotation applies to NORM statement subclasses: " + statementSuperTypeElem.getSimpleName(),
                                statementElement
                        );
                    }

                    if (statementSuperType.getTypeArguments().size() < 1) {
                        messager.printMessage(Diagnostic.Kind.ERROR,
                                "Statement must extend a NORM statement and apply required type parameters",
                                statementElement);
                        continue;
                    }

                    boolean codeGen = true;

                    Element paramsDeclType = null; // Required for all NORM statements
                    Element resultsDeclType = null; // Applies only to statements with results

                    for (TypeMirror arg : statementSuperType.getTypeArguments()) {
                        if (arg instanceof DeclaredType) {
                            DeclaredType declTypeParam = (DeclaredType) arg;

                            Element declTypeParamElem = declTypeParam.asElement();

                            if (paramsDeclType == null) {
                                paramsDeclType = declTypeParamElem;

                                // Further checks are used on the parameters
                                if (declTypeParamElem instanceof TypeElement) {
                                    TypeElement declTypeParamTypElem = (TypeElement) declTypeParamElem;

                                    for (Element member : processingEnv.getElementUtils().getAllMembers(declTypeParamTypElem)) {
                                        if (member instanceof VariableElement) {
                                            VariableElement varMember = (VariableElement) member;

                                            if (varMember.getModifiers().contains(Modifier.PRIVATE)) {
                                                messager.printMessage(Diagnostic.Kind.NOTE,
                                                        "The statement parameter type " + declTypeParamTypElem.getQualifiedName() + " has a private field with name " + member.getSimpleName() + ", which makes it impossible to code generate. Falling back to runtime reflection.",
                                                        statementElement
                                                );
                                                codeGen = false;
                                            }

                                            String vName = varMember.getSimpleName().toString();

                                            if (!referencedParms.contains(vName)) {
                                                messager.printMessage(Diagnostic.Kind.WARNING,
                                                        "The statement parameter type " + declTypeParamTypElem.getQualifiedName() + " has field with name " + member.getSimpleName() + " but the @SQL query doesn't have a matching variable :" + member.getSimpleName(),
                                                        statementElement
                                                );
                                            } else {
                                                dereferencedParms.add(vName);
                                            }

                                            TypeMirror varType = member.asType();
                                            if (varType.getKind() == TypeKind.ARRAY) {
                                                com.github.cmcgeemac.norm.Type t = varMember.getAnnotation(com.github.cmcgeemac.norm.Type.class);
                                                if (t == null) {
                                                    messager.printMessage(Diagnostic.Kind.ERROR,
                                                            "The statement parameter type " + declTypeParamTypElem.getQualifiedName() + " has a field with name " + member.getSimpleName() + " that is an array type but specifies no @Type annotation with the database type.",
                                                            statementElement);
                                                    codeGen = false;
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Results class doesn't have as many checks that we can do
                                resultsDeclType = declTypeParamElem;

                                // Further checks are used on the parameters
                                if (declTypeParamElem instanceof TypeElement) {
                                    TypeElement declTypeParamTypElem = (TypeElement) declTypeParamElem;

                                    for (Element member : processingEnv.getElementUtils().getAllMembers(declTypeParamTypElem)) {
                                        if (member instanceof VariableElement) {
                                            VariableElement varMember = (VariableElement) member;

                                            if (varMember.getModifiers().contains(Modifier.PRIVATE)) {
                                                messager.printMessage(Diagnostic.Kind.NOTE,
                                                        "The statement result type " + declTypeParamTypElem.getQualifiedName() + " has a private field with name " + member.getSimpleName() + ", which makes it impossible to code generate. Falling back to runtime reflection.",
                                                        statementElement
                                                );
                                                codeGen = false;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    referencedParms.removeAll(dereferencedParms);
                    if (!referencedParms.isEmpty()) {
                        messager.printMessage(Diagnostic.Kind.ERROR,
                                "SQL statement references the following variables from parameters class that do not exist: " + referencedParms,
                                statementElement);
                        codeGen = false;
                    }

                    AssertCodeGen cg = statementElement.getAnnotation(AssertCodeGen.class);
                    if (cg != null && !codeGen) {
                        messager.printMessage(Diagnostic.Kind.ERROR,
                                "SQL statement is declared as requiring code generation, but none will be generated. Check other compiler messages for details on the reason(s)",
                                statementElement);
                    }

                    try {
                        // Find the nearest package
                        Element pkg = statementElement;
                        while (pkg.getKind() != ElementKind.PACKAGE) {
                            pkg = pkg.getEnclosingElement();
                        }

                        // Find the enclosing class, if any
                        Element cls = (statementElement.getEnclosingElement().getKind() == ElementKind.CLASS) ? statementElement.getEnclosingElement() : null;

                        String fqParametersClass = ((PackageElement) pkg).getQualifiedName() + "." + (cls != null ? cls.getSimpleName() + "." : "") + paramsDeclType.getSimpleName();

                        String fqResultsClass = resultsDeclType != null ? ((PackageElement) pkg).getQualifiedName() + "." + (cls != null ? cls.getSimpleName() + "." : "") + resultsDeclType.getSimpleName() : "Object";

                        String handlerClass = "" + (cls != null ? cls.getSimpleName() : "") + statementElement.getSimpleName() + "NormHandler";
                        String pkgName = ((PackageElement) pkg).getQualifiedName().toString();
                        if (pkgName.length() == 0) {
                            continue;
                        }
                        String fqHandlerClass = pkgName + "." + handlerClass;

                        JavaFileObject jf;
                        Writer w;

                        try {
                            jf = filer.createSourceFile(fqHandlerClass, statementElement, cls, paramsDeclType, resultsDeclType);
                            jf.delete();
                            w = jf.openWriter();
                        } catch (IOException e) {
                            Logger.getLogger(SQLStatementProcessor.class.getName()).log(Level.SEVERE, null, e);
                            continue;
                        }

                        if (!codeGen) {
                            w.write("// Code generation is not possible for this type");
                            w.close();
                            continue;
                        }

                        w.write("package " + ((PackageElement) pkg).getQualifiedName() + ";\n");
                        w.write("\n");
                        w.write("import java.sql.Connection;\n");
                        w.write("import java.sql.PreparedStatement;\n");
                        w.write("import java.sql.ResultSet;\n");
                        w.write("import java.sql.SQLException;\n");
                        w.write("\n");
                        w.write("public class " + handlerClass + " implements com.github.cmcgeemac.norm.StatementHandler<" + fqParametersClass + "," + fqResultsClass + "> {\n");
                        w.write("    @Override\n");
                        w.write("    public void setParameters(" + fqParametersClass + " p, PreparedStatement pstmt, Connection conn) throws SQLException {\n");
                        w.write("        int idx = 1;\n");

                        Matcher m = VARIABLE_PATTERN.matcher(safeSQL);
                        while (m.find()) {
                            for (Element member : this.processingEnv.getElementUtils().getAllMembers((TypeElement) paramsDeclType)) {
                                if (!member.getSimpleName().toString().equals(m.group(1))) {
                                    continue;
                                }

                                if (member.getKind() == ElementKind.FIELD) {
                                    TypeMirror varType = member.asType();

                                    if (null == varType.getKind()) {
                                        messager.printMessage(Diagnostic.Kind.ERROR,
                                                "SQL statement references the following variables from parameters class with a type that cannot be mapped to JDBC: " + member.getSimpleName(),
                                                statementElement);
                                    } else {
                                        switch (varType.getKind()) {
                                            case INT:
                                                w.write("        pstmt.setInt(idx++, p." + member.getSimpleName() + ");\n");
                                                break;
                                            case BOOLEAN:
                                                w.write("        pstmt.setBoolean(idx++, p." + member.getSimpleName() + ");\n");
                                                break;
                                            case ARRAY:
                                                // FIXME what about primitive arrays
                                                com.github.cmcgeemac.norm.Type arrType = member.getAnnotation(com.github.cmcgeemac.norm.Type.class);
                                                if (arrType != null) {
                                                    w.write("        pstmt.setArray(idx++, conn.createArrayOf(\"" + arrType.value() + "\", p." + member.getSimpleName() + "));\n");
                                                }
                                                break;
                                            case DOUBLE:
                                                w.write("          pstmt.setDouble(idx++, p." + member.getSimpleName() + ");\n");
                                                break;
                                            case FLOAT:
                                                w.write("          pstmt.setFloat(idx++, p." + member.getSimpleName() + ");\n");
                                                break;
                                            case LONG:
                                                w.write("        pstmt.setLong(idx++, p." + member.getSimpleName() + ");\n");
                                                break;
                                            case SHORT:
                                                w.write("        pstmt.setShort(idx++, p." + member.getSimpleName() + ");\n");
                                                break;
                                            case DECLARED:
                                                DeclaredType declType = (DeclaredType) varType;
                                                Element varClassTypeElement = declType.asElement();

                                                if (varClassTypeElement instanceof TypeElement) {
                                                    TypeElement varClassType = (TypeElement) varClassTypeElement;
                                                    String fqClassType = varClassType.getQualifiedName().toString();

                                                    if (null == fqClassType) {
                                                        w.write("        pstmt.setObject(idx++, p." + member.getSimpleName() + ");\n");
                                                    } else {
                                                        switch (fqClassType) {
                                                            case "java.sql.Date":
                                                                w.write("        pstmt.setDate(idx++, p." + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.math.BigDecimal":
                                                                w.write("        pstmt.setBigDecimal(idx++, p." + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.sql.Time":
                                                                w.write("        pstmt.setTime(idx++, p." + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.lang.String":
                                                                w.write("        pstmt.setString(idx++, p." + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.sql.Timestamp":
                                                                w.write("        pstmt.setTimestamp(idx++, p." + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.net.URL":
                                                                w.write("        pstmt.setURL(idx++, p." + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.sql.Array":
                                                                w.write("        pstmt.setArray(idx++, p." + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.lang.Boolean":
                                                                w.write("        pstmt.setBoolean(idx++, p." + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.lang.Integer":
                                                                w.write("        pstmt.setInt(idx++, p." + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.lang.Double":
                                                                w.write("        pstmt.setDouble(idx++, p." + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.lang.Float":
                                                                w.write("        pstmt.setFloat(idx++, p." + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.lang.Long":
                                                                w.write("        pstmt.setLong(idx++, p." + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.lang.Short":
                                                                w.write("        pstmt.setShort(idx++, p." + member.getSimpleName() + ");\n");
                                                                break;
                                                            default:
                                                                w.write("        pstmt.setObject(idx++, p." + member.getSimpleName() + ");\n");
                                                                break;
                                                        }
                                                    }
                                                }
                                                break;
                                            default:
                                                messager.printMessage(Diagnostic.Kind.ERROR,
                                                        "SQL statement references the following variables from parameters class with a type that cannot be mapped to JDBC: " + member.getSimpleName(),
                                                        statementElement);
                                                break;
                                        }
                                    }
                                }
                            }

                            safeSQL = m.replaceFirst("?");
                            m = VARIABLE_PATTERN.matcher(safeSQL);
                        }
                        w.write("    }\n");
                        w.write("\n");
                        w.write("    @Override\n");
                        w.write("    public void result(" + fqResultsClass + " r, ResultSet rs) throws SQLException {\n");
                        if (resultsDeclType != null) {
                            for (Element member : this.processingEnv.getElementUtils().getAllMembers((TypeElement) resultsDeclType)) {
                                if (member.getKind() == ElementKind.FIELD) {
                                    TypeMirror varType = member.asType();

                                    if (null != varType.getKind()) {
                                        switch (varType.getKind()) {
                                            case INT:
                                                w.write("        r." + member.getSimpleName() + " = rs.getInt(\"" + member.getSimpleName() + "\");\n");
                                                break;
                                            case BOOLEAN:
                                                w.write("        r." + member.getSimpleName() + " = rs.getBoolean(\"" + member.getSimpleName() + "\");\n");
                                                break;
                                            case ARRAY:
                                                w.write("        r." + member.getSimpleName() + " = (" + varType.toString() + ")rs.getArray(\"" + member.getSimpleName() + "\").getArray();\n");
                                                break;
                                            case DOUBLE:
                                                w.write("        r." + member.getSimpleName() + " = rs.getDouble(\"" + member.getSimpleName() + "\");\n");
                                                break;
                                            case FLOAT:
                                                w.write("        r." + member.getSimpleName() + " = rs.getFloat(\"" + member.getSimpleName() + "\");\n");
                                                break;
                                            case LONG:
                                                w.write("        r." + member.getSimpleName() + " = rs.getLong(\"" + member.getSimpleName() + "\");\n");
                                                break;
                                            case SHORT:
                                                w.write("        r." + member.getSimpleName() + " = rs.getShort(\"" + member.getSimpleName() + "\");\n");
                                                break;
                                            case DECLARED:
                                                DeclaredType declType = (DeclaredType) varType;
                                                Element varClassTypeElement = declType.asElement();
                                                if (varClassTypeElement instanceof TypeElement) {
                                                    TypeElement varClassType = (TypeElement) varClassTypeElement;
                                                    String fqClassType = varClassType.getQualifiedName().toString();

                                                    if (null == fqClassType) {
                                                        w.write("        r." + member.getSimpleName() + " = (" + varType + ")rs.getObject(\"" + member.getSimpleName() + "\");\n");
                                                    } else {
                                                        switch (fqClassType) {
                                                            case "java.sql.Date":
                                                                w.write("        r." + member.getSimpleName() + " = getDate(" + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.math.BigDecimal":
                                                                w.write("        r." + member.getSimpleName() + " = getBigDecimal(" + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.sql.Time":
                                                                w.write("        r." + member.getSimpleName() + " = getTime(" + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.lang.String":
                                                                w.write("        r." + member.getSimpleName() + " = getString(" + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.sql.Timestamp":
                                                                w.write("        r." + member.getSimpleName() + " = getTimestamp(" + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.net.URL":
                                                                w.write("        r." + member.getSimpleName() + " = getURL(" + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.sql.Array":
                                                                w.write("        r." + member.getSimpleName() + " = getArray(" + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.lang.Boolean":
                                                                w.write("        r." + member.getSimpleName() + " = getBoolean(" + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.lang.Integer":
                                                                w.write("        r." + member.getSimpleName() + " = getnt(" + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.lang.Double":
                                                                w.write("        r." + member.getSimpleName() + " = getDouble(" + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.lang.Float":
                                                                w.write("        r." + member.getSimpleName() + " = getFloat(" + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.lang.Long":
                                                                w.write("        r." + member.getSimpleName() + " = getLong(" + member.getSimpleName() + ");\n");
                                                                break;
                                                            case "java.lang.Short":
                                                                w.write("        r." + member.getSimpleName() + " = getShort(" + member.getSimpleName() + ");\n");
                                                                break;
                                                            default:
                                                                w.write("        pstmt.setObject(idx++, p." + member.getSimpleName() + ");\n");
                                                                break;
                                                        }
                                                    }
                                                }
                                                break;
                                            default:
                                                break;
                                        }
                                    }
                                }
                            }
                        }
                        w.write("    }\n");
                        w.write("    @Override\n");
                        w.write("    public String getSafeSQL() {\n");
                        w.write("        return \"" + safeSQL.replaceAll("\"", "\\\"") + "\";\n");
                        w.write("    }\n");
                        w.write("\n");
                        w.write("}\n");
                        w.close();
                    } catch (IOException ex) {
                        Logger.getLogger(SQLStatementProcessor.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }

        return false;
    }

}
