/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.stream.StreamSupport;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;


public class TestNormStatementWithResult {

    @Test
    public void testInlineConstruction() throws Exception {
        Connection c = Mockito.mock(Connection.class);
        PreparedStatement pstmt = Mockito.mock(PreparedStatement.class);
        Mockito.when(c.prepareStatement(Mockito.any())).thenReturn(pstmt);
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        Mockito.when(pstmt.executeQuery()).thenReturn(resultSet);
        Mockito.when(resultSet.next()).thenReturn(true).thenReturn(false);
        Mockito.when(resultSet.getInt(Mockito.any())).thenReturn(1);


        class p implements Parameters {

            int baz = 100;
        }

        class r implements Result {

            int foo;
        }

        try (CloseableIterable<r> rs
        = new @SQL("SELECT foo "
                + "FROM bar WHERE "
                + "bar.baz = :baz;") NormStatementWithResult<p, r>() {
                }.execute(c)) {
            for (r r : rs) {
                Assert.assertEquals(1, r.foo);
            }
        }

        Mockito.verify(pstmt).setInt(1, 100);
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

        class p implements Parameters {

            int baz = 100;
        }

        class r implements Result {

            int foo;
        }

        try (CloseableIterable<r> rs = new @SQL(
                "SELECT foo "
                + "FROM bar "
                + "WHERE bar.baz = :baz;") NormStatementWithResult<p, r>() {
        }.execute(c)) {
            for (r r : rs) {
                Assert.assertEquals(1, r.foo);
            }
        }

        Mockito.verify(pstmt).setInt(1, 100);
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

        class p implements Parameters {

            int baz = 100;
        }

        class r implements Result {

            int foo;
        }

        Integer foo;

        try (CloseableIterable<r> rs = new @SQL(
                "SELECT foo "
                + "FROM bar "
                + "WHERE bar.baz = :baz;") NormStatementWithResult<p, r>() {
        }.execute(c)) {
            foo = StreamSupport.stream(rs
                    .spliterator(), false)
                    .map(l -> l.foo)
                    .findFirst()
                    .get();
        }

        Assert.assertNotNull(foo);
        Mockito.verify(pstmt).setInt(1, 100);
    }

    private static class QueryParameters implements Parameters {

        Integer baz = 100;
    }

    private static class QueryResult implements Result {

        Integer foo;
    }

    @SQL(
            "SELECT foo "
            + "FROM bar "
            + "WHERE bar.baz = :baz;")
    private static class Query extends NormStatementWithResult<QueryParameters, QueryResult> {
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

        try ( CloseableIterable<QueryResult> rs = QUERY.execute(c)) {
            rs.forEach(r -> Assert.assertEquals((Integer) 1, r.foo));
        }

        Mockito.verify(pstmt).setInt(1, 100);
    }

}
