/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;

import java.util.stream.StreamSupport;
import org.junit.Assert;
import org.junit.Test;


public class TestClass {

    @Test
    public void testInlineConstruction() throws Exception {
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
                }.execute(null)) {
            for (r r : rs) {
                System.out.println(r.foo);
            }
        }

    }

    @Test
    public void testInlineConstructionWithMultiLineStatement() throws Exception {
        class p implements Parameters {

            int baz;
        }

        class r implements Result {

            int foo;
        }

        try (CloseableIterable<r> rs = new @SQL(
                "SELECT foo "
                + "FROM bar "
                + "WHERE bar.baz = :baz;") NormStatementWithResult<p, r>() {
        }.execute(null)) {
            for (r r : rs) {
                System.out.println(r.foo);
            }
        }

    }

    @Test
    public void testStreaming() throws Exception {
        class p implements Parameters {

            int baz;
        }

        class r implements Result {

            int foo;
        }

        Integer foo;

        try (CloseableIterable<r> rs = new @SQL(
                "SELECT foo "
                + "FROM bar "
                + "WHERE bar.baz = :baz;") NormStatementWithResult<p, r>() {
        }.execute(null)) {
            foo = StreamSupport.stream(rs
                    .spliterator(), false)
                    .map(l -> l.foo)
                    .findFirst()
                    .get();
        }

        Assert.assertNotNull(foo);
    }

    private static class ClaimIdParameters implements Parameters {

        int baz;
    }

    private static class ClaimIdResult implements Result {

        int foo;
    }

    private static final NormStatementWithResult<ClaimIdParameters, ClaimIdResult> claimIdStatement = new @SQL(
            "SELECT foo "
            + "FROM bar "
            + "WHERE bar.baz = :baz;") NormStatementWithResult<ClaimIdParameters, ClaimIdResult>() {
    };

    @Test
    public void testReusableStatement() throws Exception {
        try (CloseableIterable<ClaimIdResult> rs = claimIdStatement.execute(null)) {
            rs.forEach(r -> System.out.println(r.foo));
        }
    }

}
