/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;

import org.junit.Test;


public class TestNormStatement {

    @Test
    public void testInlineConstruction() throws Exception {
        class p implements Parameters {
            int bar = 1;
            int newBaz = 100;
        }

        if (new @SQL("UPDATE foo SET baz = :newBaz WHERE foo.bar = :bar;") NormStatement<p>() {
        }.executeUpdate(null) != 1) {
            System.out.println("Incorrect number of rows updated. Please try again.");
        }
    }

    @Test
    public void testInlineConstructionWithMultiLineStatement() throws Exception {
        class p implements Parameters {
            int newBaz;
            int bar;
        }

        if (new @SQL(""
                + "UPDATE foo "
                + "SET baz = :newBaz "
                + "WHERE foo.bar = :bar;") NormStatement<p>() {
        }.executeUpdate(null) != 1) {
            System.out.println("Incorrect number of rows updated. Please try again.");
        }

    }

    private static class UpdateStatementParameters implements Parameters {

        int baz;
    }

    @SQL(
            "SELECT foo "
            + "FROM bar "
            + "WHERE bar.baz = :baz;")
    private static class UpdateStatement extends NormStatement<UpdateStatementParameters> {
    }

    // Let's save some construction costs each time this is run
    private static UpdateStatement UPDATE_STATEMENT = new UpdateStatement();

    @Test
    public void testReusableStatement() throws Exception {
        UPDATE_STATEMENT.executeUpdate(null);
    }
}
