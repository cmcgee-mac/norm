/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * No ORM SQL statement represents a Java typed SQL statement with parameters
 * only. For statements that produce results that can be captured see
 * {@link NormStatementWithResult}. Typically these statements are used for
 * updates to the database where the caller only needs to be aware if the
 * execution succeeded, and perhaps the number of affected rows.
 *
 * @param <P> The parameters class with the variables for this statement.
 */
public class NormStatement<P extends Parameters> {

    private static Pattern VARIABLE_PATTERN = Pattern.compile(":([a-zA-z][a-zA-z0-9]*)");

    String safeSQL; // Package access for testing
    private Object statementOuter;

    private Class<P> paramsClass;
    private Constructor<?> paramsCtor;

    private List<Field> slots = new ArrayList<Field>();

    @SuppressWarnings("unchecked")
    public NormStatement() {
        Class<?> c = getClass();
        SQL[] sql = c.getAnnotationsByType(SQL.class);

        // The SQL annotation can either be on the subclass or the parent class
        if (sql == null || sql.length == 0) {
            AnnotatedType type = c.getAnnotatedSuperclass();
            sql = type.getAnnotationsByType(SQL.class);
        }

        if (sql == null || sql.length != 1) {
            throw new IllegalArgumentException("All NormStatements must have a single SQL annotation with the SQL statement.");
        }

        safeSQL = sql[0].value();

        Type[] types = ((ParameterizedType) c.getGenericSuperclass()).getActualTypeArguments();
        if (types == null || types.length != 1) {
            throw new IllegalArgumentException("NormStatements must be anonymous classes with the generic types for parameter and result.");
        }

        try {
            Field outerThis = getClass().getDeclaredField("this$0");
            outerThis.setAccessible(true);
            statementOuter = outerThis.get(this);
        } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException ex) {
            // Best effort
        }

        paramsClass = (Class<P>) types[0];

        paramsCtor = Arrays.asList(paramsClass.getDeclaredConstructors()).stream()
                .filter(ctor -> ctor.getParameterCount() == 0 || (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0].isInstance(statementOuter)))
                .findFirst()
                .get();

        if (paramsCtor != null) {
            paramsCtor.setAccessible(true);
        }

        Matcher m = VARIABLE_PATTERN.matcher(safeSQL);
        while (m.find()) {
            String var = m.group(1);
            try {
                slots.add(paramsClass.getDeclaredField(var));
            } catch (NoSuchFieldException ex) {
                throw new IllegalArgumentException("Parameter class "
                        + paramsClass.getTypeName() + " does not have a field "
                        + var + " found in the SQL statement.");
            } catch (SecurityException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }

            safeSQL = safeSQL.replaceFirst(":" + var, "?");
        }
    }

    @SuppressWarnings("unchecked")
    public int executeUpdate(Connection c) throws SQLException {
        if (paramsCtor == null) {
            throw new IllegalArgumentException("No default constructor found for parameters object. You must create a default constructor or provide a parameters object to the execute method.");
        }

        P params = null;

        try {
            if (paramsCtor.getParameterCount() == 1) {
                params = (P) paramsCtor.newInstance(statementOuter);
            } else {
                params = (P) paramsCtor.newInstance();
            }
        } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException e) {
            throw new IllegalStateException("Unable to construct parameters instance", e);
        }

        return executeUpdate(c, params);
    }

    public int executeUpdate(Connection c, P p) throws SQLException {
        return 0;
    }
}
