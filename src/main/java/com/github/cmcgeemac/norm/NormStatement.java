/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * No ORM SQL statement represents a Java typed SQL statement with parameters
 * only. For statements that produce results that can be captured see
 * {@link NormStatementWithResult}. Typically these statements are used for
 * updates to the database where the caller only needs to be aware if the
 * execution succeeded, and perhaps the number of affected rows.
 *
 * @param <P> The parameters class with the variables for this statement.
 */
public class NormStatement<P extends Parameters> extends AbstractStatement {

    public NormStatement() {
        super();
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
        PreparedStatement pstmt;
        try {
            pstmt = execute(c, p);
        } catch (IllegalAccessException | IllegalArgumentException | SQLException ex) {
            throw new SQLException("Error preparing statement", ex);
        }

        return pstmt.executeUpdate();
    }
}
