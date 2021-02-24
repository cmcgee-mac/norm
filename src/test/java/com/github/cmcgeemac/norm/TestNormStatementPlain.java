/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;

import java.sql.Connection;
import java.sql.PreparedStatement;
import org.junit.Test;
import org.mockito.Mockito;

public class TestNormStatementPlain {

    @Test
    public void testInlineConstruction() throws Exception {
        Connection c = Mockito.mock(Connection.class);
        PreparedStatement pstmt = Mockito.mock(PreparedStatement.class);
        Mockito.when(c.prepareStatement(Mockito.any())).thenReturn(pstmt);

        new @SQL("SELECT foo "
                + "FROM bar WHERE "
                + "bar.baz = 'abc'") NormStatement<NoP, NoR>() {
        }.execute(c);

        Mockito.verify(pstmt).execute();
    }

    @Test
    public void testInlineConstructionWithMultiLineStatement() throws Exception {
        Connection c = Mockito.mock(Connection.class);
        PreparedStatement pstmt = Mockito.mock(PreparedStatement.class);
        Mockito.when(c.prepareStatement(Mockito.any())).thenReturn(pstmt);

        new @SQL(
                "SELECT foo "
                + "FROM bar "
                + "WHERE bar.baz = 'abc'") NormStatement<NoP, NoR>() {
        }.execute(c);

        Mockito.verify(pstmt).execute();
    }

    @Test
    public void testStreaming() throws Exception {
        Connection c = Mockito.mock(Connection.class);
        PreparedStatement pstmt = Mockito.mock(PreparedStatement.class);
        Mockito.when(c.prepareStatement(Mockito.any())).thenReturn(pstmt);

        Integer foo;

        new @SQL(
                "SELECT foo "
                + "FROM bar "
                + "WHERE bar.baz = 'abc'") NormStatement<NoP, NoR>() {
        }.execute(c);

        Mockito.verify(pstmt).execute();
    }

    @SQL(
            "SELECT foo "
            + "FROM bar "
            + "WHERE bar.baz = 'abc'")
    private static class Query extends NormStatement<NoP, NoR> {
    }

    // Save construction costs each time this is run
    private static final Query QUERY = new Query();

    @Test
    public void testReusableStatement() throws Exception {
        Connection c = Mockito.mock(Connection.class);
        PreparedStatement pstmt = Mockito.mock(PreparedStatement.class);
        Mockito.when(c.prepareStatement(Mockito.any())).thenReturn(pstmt);

        QUERY.execute(c);

        Mockito.verify(pstmt).execute();
    }

}
