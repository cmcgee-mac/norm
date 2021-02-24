/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestNormStatementNoParameters {

    @Test
    public void testInlineConstruction() throws Exception {
        Connection c = Mockito.mock(Connection.class);
        PreparedStatement pstmt = Mockito.mock(PreparedStatement.class);
        Mockito.when(c.prepareStatement(Mockito.any())).thenReturn(pstmt);
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        Mockito.when(pstmt.executeQuery()).thenReturn(resultSet);
        Mockito.when(resultSet.next()).thenReturn(true).thenReturn(false);
        Mockito.when(resultSet.getInt(Mockito.any())).thenReturn(1);

        class r implements NoR {

            int foo;
        }

        for (r r : new @SQL("SELECT foo "
                + "FROM bar WHERE "
                + "bar.baz = 'abc'") NormStatement<NoP, r>() {
        }.executeQuery(c)) {
            Assert.assertEquals(1, r.foo);
        }
    }

    @Test
    public void testInlineConstructionWithMultiLineStatement() throws Exception {
        Connection c = Mockito.mock(Connection.class);
        PreparedStatement pstmt = Mockito.mock(PreparedStatement.class);
        Mockito.when(c.prepareStatement(Mockito.any())).thenReturn(pstmt);
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        Mockito.when(pstmt.executeQuery()).thenReturn(resultSet);
        Mockito.when(resultSet.next()).thenReturn(true).thenReturn(false);
        Mockito.when(resultSet.getInt(Mockito.anyString())).thenReturn(1);

        class r implements NoR {

            int foo;
        }

        for (r r : new @SQL(
                "SELECT foo "
                + "FROM bar "
                + "WHERE bar.baz = 'abc'") NormStatement<NoP, r>() {
        }.executeQuery(c)) {
            Assert.assertEquals(1, r.foo);
        }
    }

    @Test
    public void testStreaming() throws Exception {
        Connection c = Mockito.mock(Connection.class);
        PreparedStatement pstmt = Mockito.mock(PreparedStatement.class);
        Mockito.when(c.prepareStatement(Mockito.any())).thenReturn(pstmt);
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        Mockito.when(pstmt.executeQuery()).thenReturn(resultSet);
        Mockito.when(resultSet.next()).thenReturn(true).thenReturn(false);
        Mockito.when(resultSet.getInt(Mockito.anyString())).thenReturn(1);

        class r implements NoR {

            int foo;
        }

        Integer foo = new @SQL(
                "SELECT foo "
                + "FROM bar "
                + "WHERE bar.baz = 'abc'") NormStatement<NoP, r>() {
        }.executeQuery(c).stream()
                .map(l -> l.foo)
                .findFirst()
                .get();

        Assert.assertNotNull(foo);
    }

    private static class QueryResult implements NoR {

        Integer foo;
    }

    @SQL(
            "SELECT foo "
            + "FROM bar "
            + "WHERE bar.baz = 'abc'")
    private static class Query extends NormStatement<NoP, QueryResult> {
    }

    // Save construction costs each time this is run
    private static final Query QUERY = new Query();

    @Test
    public void testReusableStatement() throws Exception {
        Connection c = Mockito.mock(Connection.class);
        PreparedStatement pstmt = Mockito.mock(PreparedStatement.class);
        Mockito.when(c.prepareStatement(Mockito.any())).thenReturn(pstmt);
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        Mockito.when(pstmt.executeQuery()).thenReturn(resultSet);
        Mockito.when(resultSet.next()).thenReturn(true).thenReturn(false);
        Mockito.when(resultSet.getInt(Mockito.anyString())).thenReturn(1);

        QUERY.executeQuery(c).forEach(r -> Assert.assertEquals((Integer) 1, r.foo));
    }

}
