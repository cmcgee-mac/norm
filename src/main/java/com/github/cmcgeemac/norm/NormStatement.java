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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NoORM statement with result represents a safe database statement that can
 * optionally take Java typed parameters and optionally produce Java typed
 * results. The SQL statement can only be provided through a static annotation
 * preventing it from being accidentally tainted with injected information
 * before the database has prepared it. Parameters, if needed, are provided
 * using a typed Java class that is checked against the statement to determine
 * if there are any missing or unused bindings. Results, if needed, are also
 * provided using a typed Java class making the processing of the results much
 * less reflective and easier to refactor.
 *
 * @param <P> The parameters class with the variables for this statement or
 * {@link NoP} if no parameters are necessary for this statement.
 * @param <R> The results class for this statement or {@link NoR} if no results
 * are being extracted from executing this statement.
 *
 * <p>
 * Here is a simple inline private query in a method that takes a parameter and
 * produces results.
 * </p>
 *
 * <pre>
 * class p implements NoP {
 *   int baz = matchBaz;
 * }
 *
 * class r implements NoR {
 *   int foo;
 * }
 *
 * new @SQL(&quot;SELECT foo FROM bar WHERE bar.baz = :baz&quot;) NormStatement&lt;p, r&gt;()
 * { }
 *   .executeQuery(dbConn).forEach( r -> System.out.println(r.foo) );
 * </pre>
 *
 * <p>
 * The statement is static and shared. Also, it gets better compiler checking.
 * </p>
 *
 * <pre>
 * public class Outer {
 *   static class ReqParams implements NoP {
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
 *   {@literal @}SQL(&quot;SELECT foo FROM bar WHERE bar.baz = :baz&quot;)
 *   static class Req extends NormStatement&lt;ReqParams,ReqResult&gt; {
 *   }
 *
 *   // Let's avoid repeated construction costs
 *   static final Req REQUEST = new Req();
 *
 *   public void performQuery() throws Exception {
 *     REQUEST.executeQuery(dbConn, new ReqParams().setBaz(100))
 *       .forEach( r-> System.out.println(r.foo) );
 *   }
 * }
 * </pre>
 */
public class NormStatement<P extends NoP, R extends NoR> extends AbstractStatement<P> {

    private final Class<R> resultClass;
    private final Constructor<?> resultCtor;

    @SuppressWarnings("unchecked")
    public NormStatement() {
        super();

        java.lang.reflect.Type[] types = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments();

        resultClass = (Class<R>) types[1];

        // It could be the placeholder
        if (NoR.class.equals(resultClass)) {
            resultCtor = null;
            return;
        }

        resultCtor
                = Arrays.asList(resultClass.getDeclaredConstructors()).stream()
                        .filter(ctor -> ctor.getParameterCount() == 0 || (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0].isInstance(statementOuter)))
                        .findFirst()
                        .get();

        // Force the constructor to be accessible
        resultCtor.setAccessible(true);
    }

    @SuppressWarnings("unchecked")
    private R constructResult() {

        if (resultCtor == null) {
            throw new IllegalStateException("No result class constructor could be found.");
        }

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

    public boolean execute(Connection c) throws SQLException {
        P params = null;

        // It could be just the placeholder parameters class
        if (NoP.class.equals(paramsClass)) {
            return execute(c, null);
        }

        if (paramsCtor == null) {
            throw new IllegalArgumentException("No default constructor found for parameters object. You must create a default constructor or provide a parameters object to the execute method.");
        }
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

    public boolean execute(Connection c, P p) throws SQLException {
        PreparedStatement pstmt;
        try {
            pstmt = super.createPreparedStatement(c, p);
        } catch (IllegalAccessException | IllegalArgumentException | SQLException ex) {
            throw new SQLException("Error preparing statement", ex);
        }

        try {
            return pstmt.execute();
        } catch (SQLException e) {
            throw new SQLException("Exception executing SQL statement: " + safeSQL + "; " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<R> executeQuery(Connection c) throws SQLException {
        P params = null;

        // It could be just the placeholder parameters class
        if (NoP.class.equals(paramsClass)) {
            return executeQuery(c, null);
        }

        if (paramsCtor == null) {
            throw new IllegalArgumentException("No default constructor found for parameters object. You must create a default constructor or provide a parameters object to the execute method.");
        }
        try {
            if (paramsCtor.getParameterCount() == 1) {
                params = (P) paramsCtor.newInstance(statementOuter);
            } else {
                params = (P) paramsCtor.newInstance();
            }
        } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException e) {
            throw new IllegalStateException("Unable to construct parameters instance", e);
        }

        return executeQuery(c, params);
    }

    public List<R> executeQuery(Connection c, P p) throws SQLException {

        PreparedStatement pstmt;
        try {
            pstmt = super.createPreparedStatement(c, p);
        } catch (IllegalAccessException | IllegalArgumentException | SQLException ex) {
            throw new SQLException("Error preparing statement", ex);
        }

        List<R> results = new ArrayList<>();

        try (ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                R r = constructResult();
                if (handler != null) {
                    try {
                        handler.result(r, rs);
                    } catch (SQLException ex) {
                        // TODO figure out exception strategy
                        Logger.getLogger(NormStatement.class.getName()).log(Level.SEVERE, null, ex);

                        throw new IllegalStateException(ex.getMessage(), ex);
                    }
                } else {
                    for (Field f : resultClass.getDeclaredFields()) {
                        f.setAccessible(true);

                        try {
                            Object v;
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
                            Logger.getLogger(NormStatement.class.getName()).log(Level.SEVERE, null, ex);

                            throw new IllegalStateException(ex.getMessage(), ex);
                        }
                    }
                }

                results.add(r);

            }
        } catch (SQLException e) {
            throw new SQLException("Exception execution update statement: " + safeSQL + "; " + e.getMessage(), e);
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    public int executeUpdate(Connection c) throws SQLException {

        P params = null;

        // It could be just the placeholder parameters class
        if (NoP.class.equals(paramsClass)) {
            return executeUpdate(c, null);
        }

        if (paramsCtor == null) {
            throw new IllegalArgumentException("No default constructor found for parameters object. You must create a default constructor or provide a parameters object to the execute method.");
        }

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
        PreparedStatement pstmt;
        try {
            pstmt = createPreparedStatement(c, p);
        } catch (IllegalAccessException | IllegalArgumentException | SQLException ex) {
            throw new SQLException("Error preparing statement", ex);
        }

        try {
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new SQLException("Exception execution update statement: " + safeSQL + "; " + e.getMessage(), e);
        }
    }
}
