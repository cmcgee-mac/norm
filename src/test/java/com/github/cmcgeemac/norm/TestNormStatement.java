/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;

import java.sql.Connection;
import java.sql.PreparedStatement;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestNormStatement {

    @Test
    public void testInlineConstruction() throws Exception {
        Connection c = Mockito.mock(Connection.class);
        PreparedStatement pstmt = Mockito.mock(PreparedStatement.class);
        Mockito.when(c.prepareStatement(Mockito.any())).thenReturn(pstmt);
        Mockito.when(pstmt.executeUpdate()).thenReturn(1);

        class p implements NoP {

            int bar = 1;
            int newBaz = 100;
        }

        if (new @SQL("UPDATE foo SET baz = :newBaz WHERE foo.bar = :bar;") NormStatement<p, NoR>() {
        }.executeUpdate(c) != 1) {
            Assert.fail("Incorrect number of rows updated. Please try again.");
        }
    }

    @Test
    public void testInlineConstructionWithMultiLineStatement() throws Exception {
        Connection c = Mockito.mock(Connection.class);
        PreparedStatement pstmt = Mockito.mock(PreparedStatement.class);
        Mockito.when(c.prepareStatement(Mockito.any())).thenReturn(pstmt);
        Mockito.when(pstmt.executeUpdate()).thenReturn(1);

        class p implements NoP {

            int newBaz;
            int bar;
        }

        if (new @SQL(""
                + "UPDATE foo "
                + "SET baz = :newBaz "
                + "WHERE foo.bar = :bar;") NormStatement<p, NoR>() {
        }.executeUpdate(c) != 1) {
            Assert.fail("Incorrect number of rows updated. Please try again.");
        }

    }

    private static class UpdateStatementParameters implements NoP {

        int baz;
    }

    @SQL(
            "SELECT foo "
            + "FROM bar "
            + "WHERE bar.baz = :baz;")
    private static class UpdateStatement extends NormStatement<UpdateStatementParameters, NoR> {
    }

    // Let's save some construction costs each time this is run
    private static UpdateStatement UPDATE_STATEMENT = new UpdateStatement();

    @Test
    public void testReusableStatement() throws Exception {
        Connection c = Mockito.mock(Connection.class);
        PreparedStatement pstmt = Mockito.mock(PreparedStatement.class);
        Mockito.when(c.prepareStatement(Mockito.any())).thenReturn(pstmt);
        Mockito.when(pstmt.executeUpdate()).thenReturn(1);

        Assert.assertEquals(1, UPDATE_STATEMENT.executeUpdate(c));
    }
}
