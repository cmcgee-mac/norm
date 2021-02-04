/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;

import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import net.sf.jsqlparser.util.deparser.SelectDeParser;
import net.sf.jsqlparser.util.deparser.StatementDeParser;

class Util {

    static String statementToString(Statement sqlParsed) {
        StringBuilder buffer = new StringBuilder();
        ExpressionDeParser expr = new ExpressionDeParser();

        SelectDeParser selectDeparser = new SelectDeParser(expr, buffer);
        expr.setSelectVisitor(selectDeparser);
        expr.setBuffer(buffer);
        StatementDeParser stmtDeparser = new StatementDeParser(expr, selectDeparser, buffer);

        sqlParsed.accept(stmtDeparser);
        return stmtDeparser.getBuffer().toString();
    }

    interface jdbcHandler {

        public String handle(JdbcNamedParameter param);
    }

    static void visitJdbcParameters(Statement statement, jdbcHandler jdbcHandler) {
        ExpressionDeParser ev = new ExpressionDeParser() {
            @Override
            public void visit(JdbcNamedParameter jdbcNamedParameter) {
                jdbcNamedParameter.setName(jdbcHandler.handle(jdbcNamedParameter));
            }
        };

        SelectDeParser sd = new SelectDeParser(ev, new StringBuilder()) {
            @Override
            public void visit(PlainSelect plainSelect) {
                super.visit(plainSelect);

                if (plainSelect.getLimit() != null) {
                    if (plainSelect.getLimit().getOffset() != null) {
                        plainSelect.getLimit().getOffset().accept(ev);
                    }

                    if (plainSelect.getLimit().getRowCount() != null) {
                        plainSelect.getLimit().getRowCount().accept(ev);
                    }
                }

                if (plainSelect.getOffset() != null && plainSelect.getOffset().getOffsetJdbcParameter() != null) {
                    plainSelect.getOffset().getOffsetJdbcParameter().accept(ev);
                }
            }

        };
        statement.accept(new StatementDeParser(ev, sd, new StringBuilder()));
    }
}
