/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;

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
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

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

                TypeElement tElement = (TypeElement) element;
                TypeMirror superType = tElement.getSuperclass();
                if (superType instanceof DeclaredType) {
                    DeclaredType declSuperType = (DeclaredType) superType;
                    Element declSuperTypeElem = declSuperType.asElement();
                    if (!declSuperTypeElem.getSimpleName().contentEquals("NormStatement")
                            && !declSuperTypeElem.getSimpleName().contentEquals("NormStatementWithResults")) {

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
                                        if (!annotation.value().matches("^.*:" + member.getSimpleName() + "((?![A-Za-z0-9])|$).*")) {
                                            messager.printMessage(Diagnostic.Kind.ERROR,
                                                    "The statement parameter type " + declTypeParamTypElem.getQualifiedName() + " has field with name " + member.getSimpleName() + " but the @SQL query doesn't have a matching variable :" + member.getSimpleName(),
                                                    element
                                            );
                                        }
                                    }
                                }
                            }
                        }
                    }
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
