/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;

import org.junit.Assert;
import org.junit.Test;


public class TestStatementSQLValidation {

    @Test
    public void testVariablesValidation() throws Exception {
        class p implements Parameters {
            int bar = 1;
            int newBaz = 100;
        }

        // Variables line up here
        Assert.assertEquals("UPDATE foo SET bar = ? WHERE foo.bar = ?;",
                new @SQL("UPDATE foo SET bar = :newBaz WHERE foo.bar = :bar;") NormStatement<p>() {
        }.safeSQL);

        // One of the variables could not be found in the parameters class
        try {
            new @SQL("UPDATE foo SET bar = :newBar WHERE foo.bar = :bar;") NormStatement<p>() {
            };
            Assert.fail("Missing variable not detected");
        } catch (Exception e) {
            // Expected
        }
    }
}
