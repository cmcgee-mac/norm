/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

class AbstractStatement {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile(":@@@([a-zA-z][a-zA-z0-9]*)@@@");

    final String safeSQL; // Package private for testing
    protected Object statementOuter;

    private final Class<?> paramsClass;
    protected final Constructor<?> paramsCtor;

    private List<Field> slots = new ArrayList<Field>();

    public AbstractStatement() {
        super();

        Class<?> c = getClass();
        SQL[] sql = c.getAnnotationsByType(SQL.class);

        // The SQL annotation can either be on the subclass or the parent class
        if (sql == null || sql.length == 0) {
            AnnotatedType type = c.getAnnotatedSuperclass();
            sql = type.getAnnotationsByType(SQL.class);
        }

        if (sql == null || sql.length != 1) {
            throw new IllegalArgumentException("All NormStatements must have a single SQL annotation with the SQL statement on either the base class or its immediate superclass.");
        }

        String sqlStr = sql[0].value();

        java.lang.reflect.Type[] types = ((ParameterizedType) c.getGenericSuperclass()).getActualTypeArguments();
        if (types == null || types.length > 2) {
            throw new IllegalArgumentException("NormStatements must extend and provide actual types for the generic variables of the superclass.");
        }

        // TODO this is a hack that may not work with all compilers
        try {
            Field outerThis = getClass().getDeclaredField("this$0");
            outerThis.setAccessible(true);
            statementOuter = outerThis.get(this);
        } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException ex) {
            // Best effort
            statementOuter = null;
        }

        paramsClass = (Class<?>) types[0];

        paramsCtor = Arrays.asList(paramsClass.getDeclaredConstructors()).stream()
                .filter(ctor -> ctor.getParameterCount() == 0 || (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0].isInstance(statementOuter)))
                .findFirst()
                .orElse(null);

        if (paramsCtor != null) {
            paramsCtor.setAccessible(true);
        }

        Set<String> dereferencedParms = new HashSet<>();
        Set<String> referencedParms = new HashSet<>();

        try {
            Statement sqlParsed = CCJSqlParserUtil.parse(sqlStr);
            Util.visitJdbcParameters(sqlParsed, p -> {
                String token = UUID.randomUUID().toString();
                try {
                    Field f = paramsClass.getDeclaredField(p.getName());
                } catch (NoSuchFieldException | SecurityException ex) {
                    // This will be caught later on and reported
                }
                referencedParms.add(p.getName());

                // Generate a very unique token for discovering slots later on
                return "@@@" + p.getName() + "@@@";
            });
            sqlStr = Util.statementToString(sqlParsed);
        } catch (JSQLParserException ex) {
            throw new IllegalArgumentException(
                    "@SQL annotation has a bad SQL statement: " + ex.getMessage(),
                    ex);
        }

        for (Field f : paramsClass.getDeclaredFields()) {
            if (f.isSynthetic()) {
                continue;
            }

            if (!referencedParms.contains(f.getName())) {
                Logger.getLogger(AbstractStatement.class.getName()).warning(
                        "The statement parameter type " + paramsClass.getTypeName() + " has a field with name "
                        + f.getName() + " but the @SQL query doesn't have a matching variable in " + getClass().getTypeName());
            } else {
                dereferencedParms.add(f.getName());
            }

            if (f.getType().isArray()) {
                com.github.cmcgeemac.norm.Type[] t = f.getAnnotationsByType(com.github.cmcgeemac.norm.Type.class);
                if (t == null || t.length != 1) {
                    throw new IllegalArgumentException("Parameters class field " + f.getName() + " is an array and must have a @Type annotation to set the database type of the ARRAY");
                }
            }
        }

        referencedParms.removeAll(dereferencedParms);
        if (!referencedParms.isEmpty()) {
            throw new IllegalArgumentException(
                    "SQL statement references the following variables from parameters class that do not exist: " + referencedParms);
        }

        Matcher m = VARIABLE_PATTERN.matcher(sqlStr);

        while (m.find()) {
            try {
                slots.add(paramsClass.getDeclaredField(m.group(1)));
            } catch (NoSuchFieldException ex) {
                // This should not happen because an exception would have been thrown above
            }
            sqlStr = m.replaceFirst("?");
            m = VARIABLE_PATTERN.matcher(sqlStr);
        }

        safeSQL = sqlStr;
    }

    protected PreparedStatement execute(Connection c, Object p) throws IllegalArgumentException, IllegalAccessException, SQLException {
        PreparedStatement pstmt = c.prepareStatement(safeSQL);

        int idx = 1;
        for (Field f : slots) {
            // Ensure accessibility
            f.setAccessible(true);

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
        }

        return pstmt;
    }

}
