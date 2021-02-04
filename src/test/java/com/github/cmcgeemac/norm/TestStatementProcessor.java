/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.Assert;
import org.junit.Test;

public class TestStatementProcessor {

    @Test
    public void testJdbcParameterDetection() throws Exception {
        // Variable in LIMIT
        Statement sqlParsed = CCJSqlParserUtil.parse("SELECT * "
                + "FROM foo "
                + "WHERE foo.id = :id LIMIT :bar");
        final List<String> results = new ArrayList<>();
        Util.visitJdbcParameters(sqlParsed, p -> {
            results.add(p.getName());
            return p.getName();
        });
        Assert.assertEquals(Arrays.asList("id", "bar"), results);

        // Variable in OFFSET
        sqlParsed = CCJSqlParserUtil.parse("SELECT * "
                + "FROM foo "
                + "WHERE foo.id = :id OFFSET :bar");
        results.clear();
        Util.visitJdbcParameters(sqlParsed, p -> {
            results.add(p.getName());
            return p.getName();
        });
        Assert.assertEquals(Arrays.asList("id", "bar"), results);

        // Variables in WHERE and SET
        sqlParsed = CCJSqlParserUtil.parse("UPDATE foo "
                + "SET bar = :bar "
                + "WHERE foo.id = :id");
        results.clear();
        Util.visitJdbcParameters(sqlParsed, p -> {
            results.add(p.getName());
            return p.getName();
        });
        Assert.assertEquals(Arrays.asList("bar", "id"), results);

        // Variables in INSERT
        sqlParsed = CCJSqlParserUtil.parse("INSERT INTO foo (id, bar)"
                + "VALUES(:id, :bar)");
        results.clear();
        Util.visitJdbcParameters(sqlParsed, p -> {
            results.add(p.getName());
            return p.getName();
        });
        Assert.assertEquals(Arrays.asList("id", "bar"), results);

        // Variables in SELECT
        sqlParsed = CCJSqlParserUtil.parse("SELECT :id + 1 FROM baz WHERE bar = :bar");
        results.clear();
        Util.visitJdbcParameters(sqlParsed, p -> {
            results.add(p.getName());
            return p.getName();
        });
        Assert.assertEquals(Arrays.asList("id", "bar"), results);
    }
}
