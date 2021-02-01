/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
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
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

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
public class NormStatementWithResult<P extends Parameters, R extends Result> extends AbstractStatement {

    private final Class<R> resultClass;
    private final Constructor<?> resultCtor;

    @SuppressWarnings("unchecked")
    public NormStatementWithResult() {
        super();

        java.lang.reflect.Type[] types = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments();

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

        PreparedStatement pstmt;
        try {
            pstmt = super.execute(c, p);
        } catch (IllegalAccessException | IllegalArgumentException | SQLException ex) {
            throw new SQLException("Error preparing statement", ex);
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

                            throw new IllegalStateException(ex.getMessage(), ex);
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
                                    if (rs.wasNull() && f.getType() == Integer.class) {
                                        v = null;
                                    }
                                } else if (f.getType() == float.class || f.getType() == Float.class) {
                                    v = rs.getFloat(name);
                                    if (rs.wasNull() && f.getType() == Float.class) {
                                        v = null;
                                    }
                                } else if (f.getType() == double.class || f.getType() == Double.class) {
                                    v = rs.getDouble(name);
                                    if (rs.wasNull() && f.getType() == Double.class) {
                                        v = null;
                                    }
                                } else if (f.getType() == boolean.class || f.getType() == Boolean.class) {
                                    v = rs.getBoolean(name);
                                    if (rs.wasNull() && f.getType() == Boolean.class) {
                                        v = null;
                                    }
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
                                    if (rs.wasNull() && f.getType() == Short.class) {
                                        v = null;
                                    }
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

                                throw new IllegalStateException(ex.getMessage(), ex);
                            }
                        }

                        return r;
                    }

                };
            }

        };
    }
}
