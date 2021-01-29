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
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

/**
 * No ORM SQL statement with result represents a Java typed SQL statement that
 * produces results that should be captured as result objects. The results are
 * iterable and streamable. SQL code is * provided as an * annotation to prevent
 * any possibility of contamination with * user data before * the statement is
 * prepared by the database.
 *
 * <p>
 * Here is a simple example that can be used directly in a method.
 * <pre>
 * class p implements Parameters {
 *   int baz = matchBaz;
 * }
 *
 * class r implements Result {
 *   int foo;
 * }
 *
 * try (CloseableIterable&lt;r&gt; rs = new @SQL(&quot;SELECT foo FROM bar WHERE bar.baz = :baz;&quot;) NormStatementWithResult&lt;p, r&gt;()
 * { }.execute(dbConn)) {
 *   for (r r : rs) {
 *     System.out.println(r.foo);
 *   }
 * }
 * </pre>
 * </p>
 *
 * <p>
 * The statement can be shared among different methods.
 * <pre>
 * public class Outer {
 *   static class ReqParams implements Parameters {
 *     int baz;
 *
 *     public ReqParams setBaz(int newBaz) {
 *       baz = newBaz;
 *       return this;
 *     }
 *   }
 *
 *   static class ReqResult implements Result {
 *     int foo;
 *   }
 *
 *   static NormStatementWithResults&lt;ReqParams,ReqResult&gt; REQUEST =
 *     new @SQL(&quot;SELECT foo FROM bar WHERE bar.baz = :baz;&quot;)
 *     NormStatementWithResults&lt;ReqParams,ReqResult&gt;() {}
 *
 *   public void performQuery() throws Exception {
 *     try (CloseableIterable&lt;ReqRsult&gt; rs = REQUEST.execute(dbConn, new ReqParams().setBaz(100)) {
 *       for (ReqResult r: rs) {
 *         System.out.println(r.foo);
 *       }
 *     }
 *   }
 * }
 * </pre>
 * </p>
 */
class NormStatementWithResult<P extends Parameters, R extends Result> {

    private String safeSQL;

    private Class<R> resultClass;
    private Constructor resultCtor;
    private Object resultOuter;

    private Class<P> paramsClass;
    private Constructor paramsCtor;

    public NormStatementWithResult() {
        Class<?> c = getClass();
        AnnotatedType type = c.getAnnotatedSuperclass();
        SQL[] sql = type.getAnnotationsByType(SQL.class);

        if (sql == null || sql.length != 1) {
            throw new IllegalArgumentException("All NormStatements must have a single SQL annotation with the SQL statement.");
        }

        safeSQL = sql[0].value();

        Type[] types = ((ParameterizedType) c.getGenericSuperclass()).getActualTypeArguments();
        if (types == null || types.length != 2) {
            throw new IllegalArgumentException("NormStatements must be anonymous classes with the generic types for parameter and result.");
        }

        try {
            Field outerThis = getClass().getDeclaredField("this$0");
            outerThis.setAccessible(true);
            resultOuter = outerThis.get(this);
        } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException ex) {
            // Best effort
        }

        paramsClass = (Class<P>) types[0];

        paramsCtor = Arrays.asList(paramsClass.getDeclaredConstructors()).stream()
                .filter(ctor -> ctor.getParameterCount() == 0 || (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0].isInstance(resultOuter)))
                .findFirst()
                .get();

        if (paramsCtor != null) {
            paramsCtor.setAccessible(true);
        }

        resultClass = (Class<R>) types[1];

        resultCtor
                = Arrays.asList(resultClass.getDeclaredConstructors()).stream()
                        .filter(ctor -> ctor.getParameterCount() == 0 || (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0].isInstance(resultOuter)))
                        .findFirst()
                        .get();

        if (resultCtor == null) {
            throw new IllegalArgumentException("No result class constructor could be found.");
        }

        // Force the constructor to be accessible
        resultCtor.setAccessible(true);

        // TODO validate that the parameter fields match what is in the statement using reflection
    }

    private R constructResult() {
        try {
            if (resultCtor.getParameterCount() == 1) {
                return (R) resultCtor.newInstance(resultOuter);
            } else {
                return (R) resultCtor.newInstance();
            }
        } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException e) {
            throw new IllegalStateException("Unable to construct result instance", e);
        }
    }

    public CloseableIterable<R> execute(Connection c) throws SQLException {
        if (paramsCtor == null) {
            throw new IllegalArgumentException("No default constructor found for parameters object. You must create a default constructor or provide a parameters object to the execute method.");
        }

        P params = null;

        try {
            if (paramsCtor.getParameterCount() == 1) {
                params = (P) paramsCtor.newInstance(resultOuter);
            } else {
                params = (P) paramsCtor.newInstance();
            }
        } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException e) {
            throw new IllegalStateException("Unable to construct parameters instance", e);
        }

        return execute(c, params);
    }

    public CloseableIterable<R> execute(Connection c, P p) throws SQLException {
        return new CloseableIterable<R>() {
            @Override
            public void close() throws Exception {
                // TODO
            }

            @Override
            public Iterator<R> iterator() {
                return Collections.singleton(constructResult()).iterator();
            }

        };
    }
}
