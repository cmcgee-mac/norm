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
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * No ORM SQL statement with result represents a Java typed SQL statement that
 * produces results that should be captured as result objects.The results are
 * iterable and streamable.SQL code is * provided as an * annotation to prevent
 * any possibility of contamination with * user data before * the statement is
 * prepared by the database.
 *
 * @param <P> The parameters class with the variables for this statement.
 * @param <R> The results class for this statement.
 *
 * <p>
 * Here is a simple example that can be used directly in a method.
 * </p>
 *
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
 *
 * <p>
 * The statement can be shared among different methods.
 * </p>
 *
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
 *   @SQL(&quot;SELECT foo FROM bar WHERE bar.baz = :baz;&quot;)
 *   static class Req extends NormStatementWithResults&lt;ReqParams,ReqResult&gt; {
 *   }
 *
 *   // Let's avoid repeated construction costs
 *   static final Req REQUEST = new Req();
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
 */
public class NormStatementWithResult<P extends Parameters, R extends Result> {

    private static Pattern VARIABLE_PATTERN = Pattern.compile(":([a-zA-z][a-zA-z0-9]*)");

    String safeSQL; // Package private for testing
    private Object statementOuter;

    private Class<R> resultClass;
    private Constructor<?> resultCtor;

    private Class<P> paramsClass;
    private Constructor<?> paramsCtor;

    List<Field> slots = new ArrayList<Field>();

    @SuppressWarnings("unchecked")
    public NormStatementWithResult() {
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
        if (types == null || types.length != 2) {
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

        resultClass = (Class<R>) types[1];

        resultCtor
                = Arrays.asList(resultClass.getDeclaredConstructors()).stream()
                        .filter(ctor -> ctor.getParameterCount() == 0 || (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0].isInstance(statementOuter)))
                        .findFirst()
                        .get();

        if (resultCtor == null) {
            throw new IllegalArgumentException("No result class constructor could be found.");
        }

        // Force the constructor to be accessible
        resultCtor.setAccessible(true);

        Matcher m = VARIABLE_PATTERN.matcher(safeSQL);
        while (m.find()) {
            String var = m.group(1);
            try {
                Field f = paramsClass.getDeclaredField(var);
                if (f.getType().isArray()) {
                    com.github.cmcgeemac.norm.Type[] t = f.getAnnotationsByType(com.github.cmcgeemac.norm.Type.class);
                    if (t == null || t.length != 1) {
                        throw new IllegalArgumentException("Parameters class field " + f.getName() + " is an array and must have a @Type annotation to set the database type of the ARRAY");
                    }
                }
                slots.add(f);
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
    private R constructResult() {
        try {
            if (resultCtor.getParameterCount() == 1) {
                return (R) resultCtor.newInstance(statementOuter);
            } else {
                return (R) resultCtor.newInstance();
            }
        } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException e) {
            throw new IllegalStateException("Unable to construct result instance", e);
        }
    }

    @SuppressWarnings("unchecked")
    public CloseableIterable<R> execute(Connection c) throws SQLException {
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

        return execute(c, params);
    }

    public CloseableIterable<R> execute(Connection c, P p) throws SQLException {

        PreparedStatement pstmt = c.prepareStatement(safeSQL);

        int idx = 1;
        for (Field f : slots) {
            // Ensure accessibility
            f.setAccessible(true);

            try {
                Object v = f.get(p);

                // TODO blob, clob
                if (v == null) {
                    pstmt.setNull(idx++, Types.NULL);
                } else if (v instanceof Integer) {
                    pstmt.setInt(idx++, (Integer) v);
                } else if (v instanceof Date) {
                    pstmt.setDate(idx++, (Date) v);
                } else if (v instanceof BigDecimal) {
                    pstmt.setBigDecimal(idx++, (BigDecimal) v);
                } else if (v instanceof Float) {
                    pstmt.setFloat(idx++, (Float) v);
                } else if (v instanceof Double) {
                    pstmt.setDouble(idx++, (Double) v);
                } else if (v instanceof Short) {
                    pstmt.setShort(idx++, (Short) v);
                } else if (v instanceof String) {
                    pstmt.setString(idx++, (String) v);
                } else if (v instanceof Time) {
                    pstmt.setTime(idx++, (Time) v);
                } else if (v instanceof Timestamp) {
                    pstmt.setTimestamp(idx++, (Timestamp) v);
                } else if (v instanceof URL) {
                    pstmt.setURL(idx++, (URL) v);
                } else if (v instanceof Array) {
                    pstmt.setArray(idx++, (Array) v);
                } else if (v instanceof Boolean) {
                    pstmt.setBoolean(idx++, (Boolean) v);
                } else if (v.getClass().isArray()) {
                    com.github.cmcgeemac.norm.Type[] t = f.getAnnotationsByType(com.github.cmcgeemac.norm.Type.class);
                    pstmt.setArray(idx++, c.createArrayOf(t[0].value(), (Object[]) v));
                } else {
                    pstmt.setObject(idx++, v);
                }
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                throw new SQLException(ex.getMessage(), ex);
            }
        }

        final ResultSet rs = pstmt.executeQuery();

        return new CloseableIterable<R>() {
            @Override
            public void close() throws Exception {
                rs.close();
            }

            @Override
            public Iterator<R> iterator() {
                return new Iterator<R>() {
                    @Override
                    public boolean hasNext() {
                        try {
                            return rs.next();
                        } catch (SQLException ex) {
                            // TODO figure out exception strategy
                            Logger.getLogger(NormStatementWithResult.class.getName()).log(Level.SEVERE, null, ex);
                        }

                        return false;
                    }

                    @Override
                    public R next() {
                        R r = constructResult();

                        for (Field f : resultClass.getDeclaredFields()) {
                            f.setAccessible(true);

                            try {
                                Object v = null;
                                String name = f.getName();

                                // TODO blobs, clobs
                                if (f.getType() == int.class || f.getType() == Integer.class) {
                                    v = rs.getInt(name);
                                } else if (f.getType() == float.class || f.getType() == Float.class) {
                                    v = rs.getFloat(name);
                                } else if (f.getType() == double.class || f.getType() == Double.class) {
                                    v = rs.getDouble(name);
                                } else if (f.getType() == boolean.class || f.getType() == Boolean.class) {
                                    v = rs.getBoolean(name);
                                } else if (f.getType() == String.class) {
                                    v = rs.getString(name);
                                } else if (f.getType() == Date.class) {
                                    v = rs.getDate(name);
                                } else if (f.getType() == Time.class) {
                                    v = rs.getTime(name);
                                } else if (f.getType() == Timestamp.class) {
                                    v = rs.getTimestamp(name);
                                } else if (f.getType() == BigDecimal.class) {
                                    v = rs.getBigDecimal(name);
                                } else if (f.getType() == short.class || f.getType() == Short.class) {
                                    v = rs.getShort(name);
                                } else if (f.getType() == URL.class) {
                                    v = rs.getURL(name);
                                } else if (f.getType().isArray()) {
                                    Array a = rs.getArray(name);
                                    v = a.getArray();
                                } else {
                                    v = rs.getObject(name);
                                }

                                f.set(r, v);
                            } catch (IllegalAccessException | IllegalArgumentException | SQLException ex) {
                                // TODO figure out exception strategy
                                Logger.getLogger(NormStatementWithResult.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }

                        return r;
                    }

                };
            }

        };
    }
}
