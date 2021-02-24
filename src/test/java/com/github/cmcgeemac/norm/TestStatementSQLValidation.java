/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.Assert;
import org.junit.Test;

public class TestStatementSQLValidation {

    @Test
    public void testVariablesValidation() throws Exception {
        class p implements NoP {

            int bar = 1;
            int newBaz = 100;
        }

        // Variables line up here
        Assert.assertEquals("UPDATE foo SET bar = ? WHERE foo.bar = ?",
                new @SQL("UPDATE foo SET bar = :newBaz WHERE foo.bar = :bar") NormStatement<p, NoR>() {
        }.safeSQL);

        // One of the variables could not be found in the parameters class
        try {
            new @SQL("UPDATE foo SET bar = :newBar WHERE foo.bar = :bar") NormStatement<p, NoR>() {
            };
            Assert.fail("Missing variable not detected");
        } catch (Exception e) {
            // Expected
        }

        final boolean[] warningReceived = new boolean[]{false};
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel() == Level.WARNING) {
                    warningReceived[0] = true;
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() throws SecurityException {
            }
        };

        Logger.getLogger(AbstractStatement.class.getName()).addHandler(handler);

        try {
            // The parameter class has some extra variables
            try {
                new @SQL("UPDATE foo SET bar = 1 WHERE foo.bar = :bar") NormStatement<p, NoR>() {
                };

            } catch (Exception e) {
                // Expected
            }
        } finally {
            Logger.getLogger(AbstractStatement.class.getName()).removeHandler(handler);
        }

        Assert.assertTrue(warningReceived[0]);
    }
}
