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
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

/**
 * Puts certain compile-time constraints on the SQL annotation.
 */
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("com.github.cmcgeemac.norm.SQL")
public class SQLStatementProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        Messager messager = processingEnv.getMessager();
        Filer filer = processingEnv.getFiler();

        for (TypeElement typeElement : annotations) {
            for (Element element : env.getElementsAnnotatedWith(typeElement)) {
                if (!(element instanceof TypeElement)) {
                    continue;
                }

                TypeElement tElement = (TypeElement) element;

                SQL annotation = element.getAnnotation(SQL.class);
                Set<String> referencedParms = new HashSet<>();
                Set<String> dereferencedParms = new HashSet<>();

                String safeSQL = "";

                try {
                    Statement sqlParsed = CCJSqlParserUtil.parse(annotation.value());
                    Util.visitJdbcParameters(sqlParsed, p -> {
                        referencedParms.add(p.getName());
                        return "@@@" + p.getName() + "@@@";
                    });
                    safeSQL = Util.statementToString(sqlParsed);
                } catch (JSQLParserException ex) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                            "@SQL annotation has a bad SQL statement: " + ex.getMessage(),
                            element);
                }

                TypeMirror superType = tElement.getSuperclass();
                if (superType instanceof DeclaredType) {
                    DeclaredType declSuperType = (DeclaredType) superType;
                    Element declSuperTypeElem = declSuperType.asElement();
                    if (!declSuperTypeElem.getSimpleName().contentEquals("NormStatement")
                            && !declSuperTypeElem.getSimpleName().contentEquals("NormStatementWithResult")) {

                        messager.printMessage(Diagnostic.Kind.ERROR,
                                "@SQL annotation only applies to NORM statement subclasses: " + declSuperTypeElem.getSimpleName(),
                                element
                        );
                    }

                    Element paramsDeclType = null;
                    Element resultsDeclType = null;

                    for (TypeMirror arg : declSuperType.getTypeArguments()) {
                        if (arg instanceof DeclaredType) {
                            DeclaredType declTypeParam = (DeclaredType) arg;

                            Element declTypeParamElem = declTypeParam.asElement();

                            if (paramsDeclType == null) {
                                paramsDeclType = declTypeParamElem;
                            } else {
                                resultsDeclType = declTypeParamElem;
                                break;
                            }

                            if (declTypeParamElem instanceof TypeElement) {
                                TypeElement declTypeParamTypElem = (TypeElement) declTypeParamElem;

                                for (Element member : this.processingEnv.getElementUtils().getAllMembers(declTypeParamTypElem)) {
                                    if (member instanceof VariableElement) {
                                        VariableElement varMember = (VariableElement) member;
                                        String vName = varMember.getSimpleName().toString();

                                        if (!referencedParms.contains(vName)) {
                                            messager.printMessage(Diagnostic.Kind.WARNING,
                                                    "The statement parameter type " + declTypeParamTypElem.getQualifiedName() + " has field with name " + member.getSimpleName() + " but the @SQL query doesn't have a matching variable :" + member.getSimpleName(),
                                                    element
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
                                                        element);
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
                                element);
                    }

                    try {
                        // Find the nearest package
                        Element pkg = tElement;
                        while (pkg.getKind() != ElementKind.PACKAGE) {
                            pkg = pkg.getEnclosingElement();
                        }

                        // Find the enclosing class, if any
                        Element cls = (tElement.getEnclosingElement().getKind() == ElementKind.CLASS) ? tElement.getEnclosingElement() : null;

                        String fqParametersClass = ((PackageElement) pkg).getQualifiedName() + "." + (cls != null ? cls.getSimpleName() + "." : "") + paramsDeclType.getSimpleName();

                        String fqResultsClass = resultsDeclType != null ? ((PackageElement) pkg).getQualifiedName() + "." + (cls != null ? cls.getSimpleName() + "." : "") + resultsDeclType.getSimpleName() : "Object";

                        String handlerClass = "" + (cls != null ? cls.getSimpleName() : "") + tElement.getSimpleName() + "NormHandler";
                        String pkgName = ((PackageElement) pkg).getQualifiedName().toString();
                        if (pkgName.length() == 0) {
                            continue;
                        }
                        String fqHandlerClass = pkgName + "." + handlerClass;

                        Writer w = null;

                        try {
                            JavaFileObject jf = filer.createSourceFile(fqHandlerClass, tElement, cls, paramsDeclType, resultsDeclType);
                            w = jf.openWriter();
                        } catch (IOException e) {
                            Logger.getLogger(SQLStatementProcessor.class.getName()).log(Level.SEVERE, null, e);
                        }

                        if (w != null) {
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

                                        if (varType.getKind() == TypeKind.INT) {
                                            w.write("        pstmt.setInt(idx++, p." + member.getSimpleName() + ");\n");
                                        } else if (varType.getKind() == TypeKind.BOOLEAN) {
                                            w.write("        pstmt.setBoolean(idx++, p." + member.getSimpleName() + ");\n");
                                        } else if (varType.getKind() == TypeKind.ARRAY) {
                                            // FIXME what about primitive arrays
                                            com.github.cmcgeemac.norm.Type arrType = member.getAnnotation(com.github.cmcgeemac.norm.Type.class);
                                            if (arrType != null) {
                                                w.write("        pstmt.setArray(idx++, conn.createArrayOf(\"" + arrType.value() + "\", p." + member.getSimpleName() + "));\n");
                                            }
                                        } else if (varType.getKind() == TypeKind.DOUBLE) {
                                            w.write("          pstmt.setDouble(idx++, p." + member.getSimpleName() + ");\n");
                                        } else if (varType.getKind() == TypeKind.FLOAT) {
                                            w.write("          pstmt.setFloat(idx++, p." + member.getSimpleName() + ");\n");
                                        } else if (varType.getKind() == TypeKind.LONG) {
                                            w.write("        pstmt.setLong(idx++, p." + member.getSimpleName() + ");\n");
                                        } else if (varType.getKind() == TypeKind.SHORT) {
                                            w.write("        pstmt.setShort(idx++, p." + member.getSimpleName() + ");\n");
                                        } else if (varType.getKind() == TypeKind.DECLARED) {
                                            DeclaredType declType = (DeclaredType) varType;
                                            Element varClassTypeElement = declType.asElement();

                                            if (varClassTypeElement instanceof TypeElement) {
                                                TypeElement varClassType = (TypeElement) varClassTypeElement;
                                                String fqClassType = varClassType.getQualifiedName().toString();

                                                if ("java.sql.Date".equals(fqClassType)) {
                                                    w.write("        pstmt.setDate(idx++, p." + member.getSimpleName() + ");\n");
                                                } else if ("java.math.BigDecimal".equals(fqClassType)) {
                                                    w.write("        pstmt.setBigDecimal(idx++, p." + member.getSimpleName() + ");\n");
                                                } else if ("java.sql.Time".equals(fqClassType)) {
                                                    w.write("        pstmt.setTime(idx++, p." + member.getSimpleName() + ");\n");
                                                } else if ("java.lang.String".equals(fqClassType)) {
                                                    w.write("        pstmt.setString(idx++, p." + member.getSimpleName() + ");\n");
                                                } else if ("java.sql.Timestamp".equals(fqClassType)) {
                                                    w.write("        pstmt.setTimestamp(idx++, p." + member.getSimpleName() + ");\n");
                                                } else if ("java.net.URL".equals(fqClassType)) {
                                                    w.write("        pstmt.setURL(idx++, p." + member.getSimpleName() + ");\n");
                                                } else if ("java.sql.Array".equals(fqClassType)) {
                                                    w.write("        pstmt.setArray(idx++, p." + member.getSimpleName() + ");\n");
                                                } else if ("java.lang.Boolean".equals(fqClassType)) {
                                                    w.write("        pstmt.setBoolean(idx++, p." + member.getSimpleName() + ");\n");
                                                } else if ("java.lang.Integer".equals(fqClassType)) {
                                                    w.write("        pstmt.setInt(idx++, p." + member.getSimpleName() + ");\n");
                                                } else if ("java.lang.Double".equals(fqClassType)) {
                                                    w.write("        pstmt.setDouble(idx++, p." + member.getSimpleName() + ");\n");
                                                } else if ("java.lang.Float".equals(fqClassType)) {
                                                    w.write("        pstmt.setFloat(idx++, p." + member.getSimpleName() + ");\n");
                                                } else if ("java.lang.Long".equals(fqClassType)) {
                                                    w.write("        pstmt.setLong(idx++, p." + member.getSimpleName() + ");\n");
                                                } else if ("java.lang.Short".equals(fqClassType)) {
                                                    w.write("        pstmt.setShort(idx++, p." + member.getSimpleName() + ");\n");
                                                } else {
                                                    w.write("        pstmt.setObject(idx++, p." + member.getSimpleName() + ");\n");
                                                }
                                            }
                                        } else {
                                            messager.printMessage(Diagnostic.Kind.ERROR,
                                                    "SQL statement references the following variables from parameters class with a type that cannot be mapped to JDBC: " + member.getSimpleName(),
                                                    element);
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

                                        if (varType.getKind() == TypeKind.INT) {
                                            w.write("        r." + member.getSimpleName() + " = rs.getInt(\"" + member.getSimpleName() + "\");\n");
                                        } else {

                                            w.write("        //" + member.getSimpleName() + ":" + varType + "\n");
                                        }
                                    }
                                }
                            }
                            w.write("    }\n");
                            w.write("    @Override\n");
                            w.write("    public String getSafeSQL() {\n");
                            w.write("        return \"" + safeSQL + "\";\n");
                            w.write("    }\n");
                            w.write("\n");
                            w.write("}\n");
                            w.close();
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(SQLStatementProcessor.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }

        return false;
    }

}
