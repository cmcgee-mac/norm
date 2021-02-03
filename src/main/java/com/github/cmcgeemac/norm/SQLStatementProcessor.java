/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;

import java.util.HashSet;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
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

        for (TypeElement typeElement : annotations) {
            for (Element element : env.getElementsAnnotatedWith(typeElement)) {
                if (!(element instanceof TypeElement)) {
                    continue;
                }

                SQL annotation = element.getAnnotation(SQL.class);
                Set<String> referencedParms = new HashSet<>();
                Set<String> dereferencedParms = new HashSet<>();

                try {
                    Statement sqlParsed = CCJSqlParserUtil.parse(annotation.value());
                    Util.visitJdbcParameters(sqlParsed, p -> referencedParms.add(p.getName()));
                } catch (JSQLParserException ex) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                            "@SQL annotation has bad SQL statement: " + ex.getMessage(),
                            element);
                }

                TypeElement tElement = (TypeElement) element;
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

                    for (TypeMirror arg : declSuperType.getTypeArguments()) {
                        if (arg instanceof DeclaredType) {
                            DeclaredType declTypeParam = (DeclaredType) arg;

                            Element declTypeParamElem = declTypeParam.asElement();

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

                        break;
                    }
                }

                referencedParms.removeAll(dereferencedParms);
                if (!referencedParms.isEmpty()) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                            "SQL statement references the following variables from parameters class that do not exist: " + referencedParms,
                            element);
                }
            }
        }

        return false;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return super.getSupportedSourceVersion();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return super.getSupportedAnnotationTypes();
    }

}
